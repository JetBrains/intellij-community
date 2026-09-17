// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.externalSystem.util.OutputWrapper
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ExceptionUtil
import com.intellij.util.lang.JavaVersion
import io.opentelemetry.api.trace.StatusCode
import org.gradle.api.logging.LogLevel
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.connection.GradleConnectorService
import org.jetbrains.plugins.gradle.issue.DeprecatedGradleVersionIssue
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function

/**
 * This is the low-level Gradle execution API that connects and interacts with the Gradle daemon using the Gradle tooling API.
 *
 * Consider using the high-level Gradle execution APIs instead:
 *  * [com.intellij.openapi.externalSystem.util.ExternalSystemUtil.runTask]
 *  * [com.intellij.openapi.externalSystem.util.task.TaskExecutionUtil.runTask]
 *
 * @see [Gradle tooling API](https://docs.gradle.org/current/userguide/tooling_api.html)
 */
@ApiStatus.Internal
object GradleExecutionHelper {

  private val LOG = Logger.getInstance(GradleExecutionHelper::class.java)

  /**
   * Do not use
   *
   * This flag is used only for the situation where is not possible to execute a Gradle task in an appropriate way,
   * and we have to use the bundled JDK for the execution
   */
  val AUTO_JAVA_HOME: Key<Boolean> = Key.create("AUTO_JAVA_HOME")

  @JvmStatic
  fun <Model> getModel(
    connection: ProjectConnection,
    context: GradleExecutionContext,
    modelClass: Class<Model>,
  ): Model {
    val span = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("GetModel")
      .setAttribute("modelClass", modelClass.name)
      .startSpan()
    try {
      span.makeCurrent().use {
        val modelBuilder = connection.model(modelClass)

        modelBuilder.withCancellationToken(context.cancellationToken)

        setupJavaHome(modelBuilder, context.settings, context.taskId, context.listener, null)

        val gradleProgressListener = GradleProgressListener(
          context.taskId, context.reporter, context.listener, context.projectPath
        )
        modelBuilder.addProgressListener(gradleProgressListener as ProgressListener)
        modelBuilder.addProgressListener(gradleProgressListener as org.gradle.tooling.events.ProgressListener)
        modelBuilder.setStandardOutput(OutputWrapper(context.listener, context.taskId, true))
        modelBuilder.setStandardError(OutputWrapper(context.listener, context.taskId, false))
        return modelBuilder.get()
      }
    }
    catch (ex: Exception) {
      rethrowControlFlowException(ex)
      span.recordException(ex)
      span.setStatus(StatusCode.ERROR)
      throw RuntimeException("Failed to obtain model ${modelClass.simpleName} from Gradle daemon.", ex)
    }
    finally {
      span.end()
    }
  }

  @JvmStatic
  fun <T> execute(
    context: GradleExecutionContextImpl,
    action: Function<in ProjectConnection, out T>,
  ): T {
    var buildEnvironment: BuildEnvironment? = null
    try {
      // Setting the custom build file location is deprecated since Gradle 7.6, see IDEA-359161 for more details.
      setupProjectDirectory(context)

      val connectorService = GradleConnectorService.getInstance(context.project)
      return connectorService.withGradleConnection(context) { connection ->
        SystemPropertiesAdjuster.executeAdjusted(context.projectPath) {
          buildEnvironment = getModel(connection, context, BuildEnvironment::class.java)
          context.buildEnvironment = buildEnvironment
          checkExecutionEnvironment(context)
          action.apply(connection)
        }
      }
    }
    catch (e: ExternalSystemException) {
      throw e
    }
    catch (e: BuildCancelledException) {
      throw ProcessCanceledException(e)
    }
    catch (ex: Exception) {
      rethrowControlFlowException(ex)
      throw GradleProjectResolver.createProjectResolverChain()
        .getUserFriendlyError(buildEnvironment, ex, context.projectPath, null)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Gradle execution error", e)
      val rootCause = ExceptionUtil.getRootCause(e)
      val externalSystemException = ExternalSystemException(ExceptionUtil.getMessage(rootCause), e)
      externalSystemException.initCause(e)
      throw externalSystemException
    }
  }

  private fun setupProjectDirectory(context: GradleExecutionContextImpl) {
    val projectFile = Path.of(context.projectPath)
    val projectDirectory = projectFile.parent
    if (projectFile.endsWith(GradleConstants.EXTENSION) && projectDirectory != null && Files.isRegularFile(projectFile)) {
      val settings = context.settings
      val arguments = settings.arguments
      if (!arguments.contains("-b") && !arguments.contains("--build-file")) {
        settings.withArguments("-b", projectFile.toCanonicalPath())
      }
      context.projectPath = projectDirectory.toCanonicalPath()
    }
  }

  @JvmStatic
  fun prepareForExecution(
    operation: LongRunningOperation,
    context: GradleExecutionContextImpl,
  ) {
    val effectiveContext = GradleExecutionContextImpl(context)

    val id = effectiveContext.taskId
    val settings = effectiveContext.settings
    val listener = effectiveContext.listener
    val buildEnvironment = effectiveContext.buildEnvironment

    applyIdeaParameters(settings)

    setupLogging(settings, buildEnvironment)

    GradleExecutionHelperExtension.EP_NAME.forEachExtensionSafe { proc ->
      proc.configureSettings(settings, effectiveContext)
    }

    clearSystemProperties(operation)

    setupJvmArguments(operation, settings)

    setupArguments(operation, settings)

    setupEnvironment(operation, settings)

    setupJavaHome(operation, settings, id, listener, buildEnvironment)

    setupProgressListeners(operation, settings, effectiveContext)

    setupStandardIO(operation, settings, id, listener)

    operation.withCancellationToken(effectiveContext.cancellationToken)

    GradleExecutionHelperExtension.EP_NAME.forEachExtensionSafe { proc ->
      proc.configureOperation(operation, effectiveContext)
    }
  }

  private fun clearSystemProperties(operation: LongRunningOperation) {
    // for Gradle 7.6+ this will cancel implicit transfer of current System.properties to Gradle Daemon.
    operation.withSystemProperties(emptyMap())
  }

  private fun applyIdeaParameters(settings: GradleExecutionSettings) {
    if (settings.isOfflineWork) {
      settings.withArgument(GradleConstants.OFFLINE_MODE_CMD_OPTION)
    }
    val applicationInfo = ApplicationInfoImpl.getShadowInstance()
    settings.withArgument("-Didea.active=true")
    settings.withArgument("-Didea.version=${applicationInfo.majorVersion}.${applicationInfo.minorVersion}")
    settings.withArgument("-Didea.vendor.name=${applicationInfo.shortCompanyName}")
  }

  private fun setupProgressListeners(
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
    context: GradleExecutionContext,
  ) {
    val buildRootDir = getBuildRoot(context.buildEnvironment)
    val progressListener = GradleProgressListener(
      context.taskId, context.reporter, context.listener, buildRootDir.toString()
    )
    operation.addProgressListener(progressListener as ProgressListener)
    operation.addProgressListener(
      progressListener,
      OperationType.TASK,
      OperationType.FILE_DOWNLOAD
    )
    if (settings.isRunAsTest && settings.isBuiltInTestEventsUsed) {
      operation.addProgressListener(
        progressListener,
        OperationType.TEST,
        OperationType.TEST_OUTPUT,
        OperationType.TASK
      )
    }
  }

  private fun setupStandardIO(
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
    id: ExternalSystemTaskId,
    listener: ExternalSystemTaskNotificationListener,
  ) {
    operation.setStandardOutput(OutputWrapper(listener, id, true))
    operation.setStandardError(OutputWrapper(listener, id, false))
    val inputStream = settings.getUserData(ExternalSystemRunConfiguration.RUN_INPUT_KEY)
    if (inputStream != null) {
      operation.setStandardInput(inputStream)
    }
  }

  @VisibleForTesting
  fun setupJvmArguments(
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
  ) {
    val jvmArgs = settings.jvmArguments.filter { it.isNotEmpty() }
    if (jvmArgs.isNotEmpty()) {
      operation.addJvmArguments(*ArrayUtilRt.toStringArray(jvmArgs))
    }
  }

  private fun setupJavaHome(
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
    id: ExternalSystemTaskId,
    listener: ExternalSystemTaskNotificationListener,
    buildEnvironment: BuildEnvironment?,
  ) {
    val javaHome = getJavaHomeForOperation(settings, id, listener, buildEnvironment) ?: return
    @Suppress("IO_FILE_USAGE")
    operation.setJavaHome(java.io.File(javaHome))
    LOG.debug("Java home to set for Gradle operation: $javaHome")
  }

  private fun getJavaHomeForOperation(
    settings: GradleExecutionSettings,
    id: ExternalSystemTaskId,
    listener: ExternalSystemTaskNotificationListener,
    buildEnvironment: BuildEnvironment?,
  ): String? {
    if (settings.getUserData(AUTO_JAVA_HOME) == true && buildEnvironment != null) {
      val project = id.project
      val gradle = buildEnvironment.gradle
      val gradleVersion = GradleVersion.version(gradle.gradleVersion)

      for (sdkPath in ExternalSystemJdkUtil.suggestJdkHomePaths(project)) {
        val javaVersion = ExternalSystemJdkUtil.getJavaVersion(sdkPath)
        if (javaVersion != null && GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)) {
          listener.onTaskOutput(
            id,
            GradleBundle.message("gradle.auto.jdk.was.selected", sdkPath) + System.lineSeparator(),
            ProcessOutputType.STDOUT
          )
          return sdkPath
        }
      }
    }
    return settings.javaHome
  }

  private fun setupArguments(
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
  ) {
    val commandLine = fixUpGradleCommandLine(settings.commandLine)

    LOG.info("Passing command-line to Gradle Tooling API: " + StringUtil.join(obfuscatePasswordParameters(commandLine.tokens), " "))

    when (operation) {
      is TestLauncher -> setupTestLauncherArguments(operation, commandLine)
      is BuildLauncher -> setupBuildLauncherArguments(operation, commandLine, settings)
      else -> operation.withArguments(commandLine.tokens)
    }
  }

  private fun fixUpGradleCommandLine(commandLine: GradleCommandLine): GradleCommandLine {
    val tasks = commandLine.tasks.map { task ->
      GradleCommandLineTask(task.name, task.options.filterNot { it.isWildcardTestPattern() })
    }
    return GradleCommandLine(tasks, commandLine.options)
  }

  private fun setupTestLauncherArguments(
    testLauncher: TestLauncher,
    commandLine: GradleCommandLine,
  ) {
    for (task in commandLine.tasks) {
      val patterns = task.getTestPatterns()
      if (patterns.isNotEmpty()) {
        testLauncher.withTestsFor { it.forTaskPath(task.name).includePatterns(patterns) }
      }
      else {
        testLauncher.forTasks(*task.tokens.toTypedArray())
      }
    }
    testLauncher.withArguments(commandLine.options.tokens)
  }

  private fun setupBuildLauncherArguments(
    buildLauncher: BuildLauncher,
    commandLine: GradleCommandLine,
    settings: GradleExecutionSettings,
  ) {
    buildLauncher.forTasks(*commandLine.tasks.tokens.toTypedArray())
    buildLauncher.withArguments(commandLine.options.tokens)
    if (settings.isTestTaskRerun) {
      val initScript = createTestInitScript()
      buildLauncher.addArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.toString())
    }
  }

  @VisibleForTesting
  fun setupLogging(
    settings: GradleExecutionSettings,
    buildEnvironment: BuildEnvironment?,
  ) {
    val arguments = settings.arguments
    val options = GradleCommandLineOptionsProvider.LOGGING_OPTIONS.options
    val optionsNames = GradleCommandLineOptionsProvider.getAllOptionsNames(options)

    if (optionsNames.any { arguments.contains(it) }) {
      return
    }

    // workaround for https://github.com/gradle/gradle/issues/19340
    // when using TAPI, user-defined log level option in gradle.properties is ignored by Gradle.
    // try to read this file manually and apply log level explicitly
    val buildRoot = getBuildRoot(buildEnvironment)
    if (buildRoot != null) {
      val properties = GradlePropertiesFile.getProperties(settings.serviceDirectory, buildRoot)
      val logLevel = properties.getGradleLogLevel()
      if (logLevel != null) {
        when (logLevel) {
          LogLevel.DEBUG -> settings.withArgument("-d")
          LogLevel.INFO -> settings.withArgument("-i")
          LogLevel.WARN -> settings.withArgument("-w")
          LogLevel.QUIET -> settings.withArgument("-q")
          else -> {}
        }
      }
    }

    if (optionsNames.any { arguments.contains(it) }) {
      return
    }

    // Default logging level for integration tests
    val application = ApplicationManager.getApplication()
    if (application != null && application.isUnitTestMode) {
      settings.withArgument("--info")
    }
  }

  private fun getBuildRoot(buildEnvironment: BuildEnvironment?): Path? {
    return buildEnvironment?.buildIdentifier?.rootDir?.toPath()
  }

  private fun setupEnvironment(
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
  ) {
    val environmentConfigurationProvider = settings.getEnvironmentConfigurationProvider()
    val environmentConfiguration = environmentConfigurationProvider?.environmentConfiguration
    if (environmentConfiguration != null && LocalGradleExecutionAware.LOCAL_TARGET_TYPE_ID != environmentConfiguration.typeId) {
      if (settings.isPassParentEnvs) {
        LOG.warn("Host system environment variables will not be passed for the target run.")
      }
      operation.setEnvironmentVariables(settings.env)
      return
    }

    val commandLine = GeneralCommandLine()
    commandLine.withEnvironment(settings.env)
    commandLine.withParentEnvironmentType(
      when (settings.isPassParentEnvs) {
        true -> GeneralCommandLine.ParentEnvironmentType.CONSOLE
        else -> GeneralCommandLine.ParentEnvironmentType.NONE
      }
    )
    val effectiveEnvironment = commandLine.effectiveEnvironment
    operation.setEnvironmentVariables(effectiveEnvironment)
  }

  private fun checkExecutionEnvironment(context: GradleExecutionContext) {
    checkThatGradleBuildEnvironmentIsSupportedByIdea(context)
    checkThatGradleBuildEnvironmentIsDeprecatedByIdea(context)
    GradleExecutionChecker.EP_NAME.forEachExtensionSafe { checker ->
      checker.checkExecutionEnvironment(context)
    }
  }

  private fun checkThatGradleBuildEnvironmentIsDeprecatedByIdea(context: GradleExecutionContext) {
    val gradleVersion = context.gradleVersion
    if (GradleJvmSupportMatrix.isGradleDeprecatedByIdea(gradleVersion)) {
      val projectPath = context.projectPath
      val issue = DeprecatedGradleVersionIssue(gradleVersion, projectPath)
      context.listener.onStatusChange(
        ExternalSystemBuildEvent(
          context.taskId,
          BuildIssueEventImpl(context.taskId, issue, MessageEvent.Kind.WARNING)
        )
      )
    }
  }

  private fun checkThatGradleBuildEnvironmentIsSupportedByIdea(context: GradleExecutionContext) {
    val gradleVersion = context.gradleVersion
    LOG.debug("Gradle version: $gradleVersion")
    if (!GradleJvmSupportMatrix.isGradleSupportedByIdea(gradleVersion)) {
      throw UnsupportedGradleVersionByIdeaException(gradleVersion)
    }
    val javaHome = context.buildEnvironment.java.javaHome
    val jvmArguments = context.buildEnvironment.java.jvmArguments
    LOG.debug("Gradle java home: $javaHome")
    LOG.debug("Gradle jvm arguments: $jvmArguments")
    val javaVersion = ExternalSystemJdkUtil.getJavaVersion(javaHome.path)
    if (javaVersion != null && !GradleJvmSupportMatrix.isJavaSupportedByIdea(javaVersion)) {
      throw UnsupportedGradleJvmByIdeaException(gradleVersion, javaVersion)
    }
  }

  @VisibleForTesting
  @JvmStatic
  fun obfuscatePasswordParameters(commandLineArguments: List<String>): List<String> {
    val replaced = ArrayList<String>(commandLineArguments.size)
    val passwordParameterIdentifier = ".password="
    for (option in commandLineArguments) {
      // Find parameters ending in "password", like:
      //   -Pandroid.injected.signing.store.password=
      //   -Pandroid.injected.signing.key.password=
      val index = option.indexOf(passwordParameterIdentifier)
      if (index == -1) {
        replaced.add(option)
      }
      else {
        replaced.add(option.substring(0, index + passwordParameterIdentifier.length) + "*********")
      }
    }
    return replaced
  }

  class UnsupportedGradleVersionByIdeaException(val gradleVersion: GradleVersion) : RuntimeException("Unsupported Gradle version")

  class UnsupportedGradleJvmByIdeaException(
    val gradleVersion: GradleVersion,
    val javaVersion: JavaVersion?,
  ) : RuntimeException("Unsupported Gradle JVM version")
}
