// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.coverage

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageExecutor
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.JaCoCoCoverageRunner
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.gradle.java.coverage.GradleCoverageWithJaCoCoPluginTest.Companion.TEST_TASK
import com.intellij.gradle.java.coverage.GradleCoverageWithJaCoCoPluginTest.Companion.jvmArgumentsCollectorScript
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolder
import com.intellij.ui.classFilter.ClassFilter
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.execution.toGroovyStringLiteral
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager.INIT_SCRIPT_KEY
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager.INIT_SCRIPT_PREFIX_KEY
import org.jetbrains.plugins.gradle.testFramework.GradleTestExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.assertThatConfigurationCacheIsSupported
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readLines

/**
 * Checks the coverage run of a Gradle test task, with and without the Gradle `jacoco` plugin.
 *
 * The IDE adds its own JaCoCo agent to the test task as a plain `-javaagent` JVM argument.
 * A project that applies the Gradle `jacoco` plugin already has an agent on the same task.
 * Gradle contributes it through `Test.jvmArgumentProviders`, so it is absent from `Test.jvmArgs`.
 * Two JaCoCo agents in one JVM both define the class `java.lang.$JaCoCo`, and the JVM aborts.
 *
 * [GradleJaCoCoCoverageTaskManagerExtension] turns off the agent of the Gradle plugin, so that the IDE agent
 * is the only JaCoCo agent. See IDEA-269284.
 */
class GradleCoverageWithJaCoCoPluginTest : GradleTestExecutionTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test the IDE agent collects the coverage when the project has the jacoco plugin`(
    gradleVersion: GradleVersion,
  ) {
    test(gradleVersion, JACOCO_PLUGIN_FIXTURE) {
      writeCalculatorSources()

      val run = executeTestTaskWithCoverage()

      assertIdeJaCoCoAgentOf(run)
      assertCoverageOfCalculator(run)
      assertTestEventsContain(CALCULATOR_TEST_CLASS, "add")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test the IDE agent collects the coverage when the project has no jacoco plugin`(
    gradleVersion: GradleVersion,
  ) {
    testJavaProject(gradleVersion) {
      writeCalculatorSources()

      val run = executeTestTaskWithCoverage()

      assertIdeJaCoCoAgentOf(run)
      assertCoverageOfCalculator(run)
      assertTestEventsContain(CALCULATOR_TEST_CLASS, "add")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test the IDE agent collects the coverage for any JaCoCo tool version`(
    gradleVersion: GradleVersion,
  ) {
    test(gradleVersion, JACOCO_PLUGIN_FIXTURE) {
      writeCalculatorSources()
      appendText("build.gradle", """
        |jacoco {
        |  toolVersion = '$OLD_TOOL_VERSION'
        |}
      """.trimMargin())

      val run = executeTestTaskWithCoverage()

      assertIdeJaCoCoAgentOf(run)
      assertCoverageOfCalculator(run)
    }
  }

  /**
   * Gradle stores the task actions of the init script in the configuration cache and runs the
   * tasks from the stored entry. When an action does something that the cache does not support,
   * for example a call of `Task.project`, Gradle reports a problem and fails the build.
   *
   * The second run checks that the run of a reused or a new cache entry also collects the coverage.
   */
  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test the coverage run is compatible with the configuration cache`(
    gradleVersion: GradleVersion,
  ) {
    assertThatConfigurationCacheIsSupported(gradleVersion)
    test(gradleVersion, JACOCO_PLUGIN_FIXTURE) {
      writeCalculatorSources()
      writeText("gradle.properties", """
        |org.gradle.configuration-cache=true
      """.trimMargin())

      repeat(2) {
        val run = executeTestTaskWithCoverage(isConfigurationCacheEnabled = true)

        assertNoConfigurationCacheProblemsIn(run)
        assertIdeJaCoCoAgentOf(run)
        assertCoverageOfCalculator(run)
      }
    }
  }

  /**
   * The result of one coverage run of the Gradle test task.
   *
   * The IDE deletes the coverage data file together with the coverage suite, so this holds the
   * content of that file instead of its path.
   *
   * @property output the whole Gradle output
   * @property jvmArguments the JVM arguments of the forked test JVM, as `Test.allJvmArgs` holds them
   * @property coverageData the content of the coverage data file, or null when the run wrote no file
   * @property isCoverageDataReadable true when the IDE reads the coverage data file of the run
   */
  private class CoverageRun(
    val output: String,
    val jvmArguments: List<String>,
    val coverageData: ByteArray?,
    val isCoverageDataReadable: Boolean,
  ) {
    /**
     * The JaCoCo agents of the run, without the quotes.
     *
     * On Windows the IDE quotes a JVM argument with a special character, for example `~` or `*`.
     */
    val jaCoCoAgents: List<String>
      get() = jvmArguments
        .map { it.removeSurrounding("\"") }
        .filter { it.startsWith(JAVA_AGENT_PREFIX) && it.contains(JACOCO, ignoreCase = true) }

    /** The agent jar of the single JaCoCo agent of the run. */
    val jaCoCoAgentJar: String
      get() = jaCoCoAgents.single().removePrefix(JAVA_AGENT_PREFIX).substringBefore('=')
  }

  /**
   * Checks that the IDE agent is the only JaCoCo agent of the run.
   *
   * The IDE unpacks its agent into the temporary directory, outside the project.
   * The Gradle plugin unpacks its agent into the build directory of the project.
   */
  private fun assertIdeJaCoCoAgentOf(run: CoverageRun) {
    assertThat(run.jaCoCoAgents)
      .describedAs("The forked test JVM must get one JaCoCo agent. All JVM arguments: %s", run.jvmArguments)
      .hasSize(1)
    assertThat(run.output)
      .describedAs("The forked test JVM must start")
      .doesNotContain(DUPLICATE_CLASS_ERROR)
    assertThat(Path.of(run.jaCoCoAgentJar).startsWith(projectNioPath))
      .describedAs("The IDE agent must collect the coverage of the run")
      .isFalse()
  }

  private fun assertNoConfigurationCacheProblemsIn(run: CoverageRun) {
    assertThat(run.output)
      .describedAs("The init scripts of the run must be compatible with the configuration cache")
      .doesNotContainPattern(CONFIGURATION_CACHE_PROBLEMS_FOUND)
    assertThat(run.output)
      .describedAs("The run must use the configuration cache")
      .containsAnyOf(CONFIGURATION_CACHE_ENTRY_STORED, CONFIGURATION_CACHE_ENTRY_REUSED)
  }

  private fun assertCoverageOfCalculator(run: CoverageRun) {
    assertThat(run.coverageData)
      .describedAs("The run must write the coverage data. Gradle output: %s", run.output)
      .isNotNull()
    assertThat(run.isCoverageDataReadable)
      .describedAs("The IDE must read the coverage data of the run")
      .isTrue()
    val coverageData = run.coverageData!!.toLatin1()
    assertThat(coverageData)
      .describedAs("The coverage data must hold the covered class")
      .contains("org/example/Calculator")
    assertThat(coverageData)
      .describedAs("The coverage filters of the run must apply")
      .doesNotContain("org/gradle/")
  }

  private fun writeCalculatorSources() {
    writeText("src/main/java/org/example/Calculator.java", """
      |package org.example;
      |public class Calculator {
      |  public int add(int left, int right) {
      |    return left + right;
      |  }
      |}
    """.trimMargin())
    writeText("src/test/java/org/example/CalculatorTest.java", """
      |package org.example;
      |import $jUnitTestAnnotationClass;
      |public class CalculatorTest {
      |  @Test
      |  public void add() {
      |    new Calculator().add(1, 2);
      |  }
      |}
    """.trimMargin())
  }

  /**
   * Runs [TEST_TASK] with the coverage executor and the JaCoCo coverage runner.
   *
   * An init script collects the JVM arguments of the forked test JVM, because that JVM can abort
   * before a test reports them itself.
   *
   * @param isConfigurationCacheEnabled true when the project enables the configuration cache.
   * The init script then uses a task action instead of a build listener, which the cache rejects.
   */
  private fun executeTestTaskWithCoverage(
    isConfigurationCacheEnabled: Boolean = false,
  ): CoverageRun {
    // A second run in the same test must not read the arguments of the first run.
    val jvmArgumentsPath = findOrCreateFile("testTaskJvmArguments.txt").toNioPath()
    jvmArgumentsPath.deleteIfExists()
    val runnerSettings = executionFixture.createRunnerSettings(TEST_TASK, isRunAsTest = true)
    val runConfiguration = runnerSettings.configuration as GradleRunConfiguration

    // GradleRunConfiguration inherits a raw LocatableConfigurationBase, so Kotlin erases the
    // generic putUserData of RunConfigurationBase. The UserDataHolder view keeps the signature.
    val runConfigurationUserData: UserDataHolder = runConfiguration
    runConfigurationUserData.putUserData(INIT_SCRIPT_PREFIX_KEY, "ijTestTaskJvmArgumentsCollector")
    val collectorScript = if (isConfigurationCacheEnabled) {
      configurationCacheCompatibleJvmArgumentsCollectorScript(jvmArgumentsPath.toString())
    }
    else {
      jvmArgumentsCollectorScript(jvmArgumentsPath.toString())
    }
    runConfigurationUserData.putUserData(INIT_SCRIPT_KEY, collectorScript)

    val coverageConfiguration = requireNotNull(JavaCoverageEnabledConfiguration.getFrom(runConfiguration)) {
      "The Java coverage engine rejected the Gradle run configuration. " +
      "Check that ${GradleCoverageExtension::class.java.name} is registered."
    }
    coverageConfiguration.coverageRunner = CoverageRunner.getInstance(JaCoCoCoverageRunner::class.java)
    coverageConfiguration.coveragePatterns = arrayOf(ClassFilter("org.example.*"))

    val executor = requireNotNull(ExecutorRegistry.getInstance().getExecutorById(CoverageExecutor.EXECUTOR_ID)) {
      "The coverage executor is absent. Check that the Coverage plugin is loaded."
    }
    val environment = runReadActionBlocking {
      ExecutionEnvironmentBuilder.create(executor, runnerSettings).build()
    }

    try {
      val output = collectGradleOutput {
        executionFixture.execute(environment)
      }
      val coverageFile = Path.of(requireNotNull(coverageConfiguration.coverageFilePath) {
        "The coverage run has no coverage data file"
      })
      val runner = CoverageRunner.getInstance(JaCoCoCoverageRunner::class.java)
      return CoverageRun(
        output = output,
        jvmArguments = if (jvmArgumentsPath.exists()) jvmArgumentsPath.readLines() else emptyList(),
        coverageData = if (coverageFile.exists()) coverageFile.readBytes() else null,
        isCoverageDataReadable = coverageFile.exists() && runner.canBeLoaded(coverageFile),
      )
    }
    finally {
      coverageConfiguration.currentCoverageSuite
        ?.let { CoverageDataManager.getInstance(project).removeCoverageSuite(it) }
    }
  }

  private fun collectGradleOutput(action: () -> Unit): String {
    val output = StringBuilder()
    val disposable = Disposer.newDisposable("GradleCoverageWithJaCoCoPluginTest output listener")
    try {
      ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(
        object : ExternalSystemTaskNotificationListener {
          override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
            synchronized(output) { output.append(text) }
          }
        },
        disposable
      )
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
    return synchronized(output) { output.toString() }
  }

  companion object {

    private const val TEST_TASK = ":test"
    private const val CALCULATOR_TEST_CLASS = "org.example.CalculatorTest"
    private const val JAVA_AGENT_PREFIX = "-javaagent:"

    /** The IDE extracts its agent as `jacocoagent<random>.jar`, so only the vendor name is stable. */
    private const val JACOCO = "jacoco"
    private const val DUPLICATE_CLASS_ERROR = $$"duplicate class definition for java.lang.$JaCoCo"

    private const val CONFIGURATION_CACHE_ENTRY_STORED = "Configuration cache entry stored"
    private const val CONFIGURATION_CACHE_ENTRY_REUSED = "Configuration cache entry reused"
    /** Gradle writes, for example, `1 problem was found storing the configuration cache.` */
    private const val CONFIGURATION_CACHE_PROBLEMS_FOUND = "problems? (was|were) found (storing|reusing) the configuration cache"

    /**
     * A `jacoco { toolVersion }` that is older than the bundled JaCoCo.
     *
     * The agent of this version cannot start on a recent test JVM.
     * The run must not resolve or start it, because the IDE agent collects the data.
     */
    private const val OLD_TOOL_VERSION = "0.8.7"

    private val JACOCO_PLUGIN_FIXTURE = GradleTestFixtureBuilder.create("jacoco-plugin-project") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("jacoco-plugin-project")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withPlugin("jacoco")
        withJUnit()
      }
      withDirectory("src/main/java")
      withDirectory("src/test/java")
    }

    private fun ByteArray.toLatin1(): String = String(this, StandardCharsets.ISO_8859_1)

    /**
     * Builds an init script that writes the JVM arguments of the forked test JVM to [outputPath].
     *
     * `Test.allJvmArgs` is the only view that holds both `Test.jvmArgs` and the arguments of
     * `Test.jvmArgumentProviders`. The Gradle `jacoco` plugin uses a provider, so `Test.jvmArgs`
     * alone hides its agent.
     *
     * `afterTask` runs after every action of the task, and also after the task fails. The file
     * therefore holds the final arguments, even when the forked JVM aborts.
     */
    private fun jvmArgumentsCollectorScript(outputPath: String): String {
      @Language("Groovy")
      val template = """
        |import java.nio.file.Files
        |import java.nio.file.Paths
        |import org.gradle.process.JavaForkOptions
        |
        |//noinspection GrUnresolvedAccess
        |gradle.taskGraph.afterTask { Task task ->
        |  //noinspection GrUnresolvedAccess
        |  if (task instanceof JavaForkOptions && task.path == 'TASK_PATH') {
        |    //noinspection GrUnresolvedAccess, GroovyAssignabilityCheck
        |    Files.write(Paths.get('OUTPUT_PATH'), task.allJvmArgs.join("\n").bytes)
        |  }
        |}
      """.trimMargin()
      return template
        .replace("'TASK_PATH'", TEST_TASK.toGroovyStringLiteral())
        .replace("'OUTPUT_PATH'", outputPath.toGroovyStringLiteral())
    }

    /**
     * Builds an init script that writes the JVM arguments of the forked test JVM to [outputPath].
     *
     * The configuration cache rejects `gradle.taskGraph.afterTask`, so this script uses a `doLast`
     * action. The action refers to the task through `delegate` and holds no `Task` object.
     * Unlike [jvmArgumentsCollectorScript], it writes nothing when the test task fails.
     */
    private fun configurationCacheCompatibleJvmArgumentsCollectorScript(outputPath: String): String {
      @Language("Groovy")
      val template = """
        |import java.nio.file.Files
        |import java.nio.file.Paths
        |import org.gradle.process.JavaForkOptions
        |
        |//noinspection GrUnresolvedAccess
        |gradle.taskGraph.whenReady { taskGraph ->
        |  taskGraph.allTasks.each { Task task ->
        |    //noinspection GrUnresolvedAccess
        |    if (task instanceof JavaForkOptions && task.path == 'TASK_PATH') {
        |      def outputPath = 'OUTPUT_PATH'
        |      //noinspection GrUnresolvedAccess
        |      task.doLast {
        |        //noinspection GrUnresolvedAccess, GroovyAssignabilityCheck
        |        Files.write(Paths.get(outputPath), delegate.allJvmArgs.join("\n").bytes)
        |      }
        |    }
        |  }
        |}
      """.trimMargin()
      return template
        .replace("'TASK_PATH'", TEST_TASK.toGroovyStringLiteral())
        .replace("'OUTPUT_PATH'", outputPath.toGroovyStringLiteral())
    }
  }
}
