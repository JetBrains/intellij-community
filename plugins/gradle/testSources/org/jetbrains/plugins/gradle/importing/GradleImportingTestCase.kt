// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.compiler.CompilerTestUtil
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.execution.RunManagerEx
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver.unknownSdkFixMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import com.intellij.openapi.externalSystem.test.JavaExternalSystemImportingTestCase
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.environment.Environment.Companion.getVariable
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.testFramework.io.ExternalResourcesChecker.reportUnavailability
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.util.SmartList
import com.intellij.util.ThrowableRunnable
import com.intellij.util.currentJavaVersion
import com.intellij.util.io.copyRecursively
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.delete
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.create
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix.Companion.isSupported
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.service.project.wizard.util.generateGradleWrapper
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.tooling.GradleJvmResolver.Companion.resolveGradleJvmHomePath
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.jetbrains.plugins.gradle.tooling.TargetJavaVersionWatcher
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.util.*
import org.junit.Assume
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.io.path.exists

@RunWith(Parameterized::class)
abstract class GradleImportingTestCase : JavaExternalSystemImportingTestCase() {

  @JvmField
  @Rule
  var name: TestName = TestName()

  private val versionMatcherRule: VersionMatcherRule = asOuterRule<VersionMatcherRule>(VersionMatcherRule())
  private val myTargetJavaVersionWatcher: TargetJavaVersionWatcher = asOuterRule<TargetJavaVersionWatcher>(TargetJavaVersionWatcher())

  @Parameterized.Parameter
  @JvmField
  var myGradleVersion: String? = null

  open var gradleVersion: String
    get() = myGradleVersion!!
    set(value) {
      myGradleVersion = value
    }

  val currentGradleVersion: GradleVersion
    get() = GradleVersion.version(gradleVersion)

  protected val currentGradleBaseVersion: GradleVersion
    get() = GradleVersion.version(gradleVersion).baseVersion

  var gradleJdkHome: String? = null
    private set

  private val removedSdks: MutableList<Sdk> = SmartList<Sdk>()
  private val myTestDisposable by lazy { Disposer.newDisposable() }
  private val deprecationError = Ref.create<Couple<String>?>()
  private val deprecationTextBuilder = StringBuilder()

  private var myProjectSettings: GradleProjectSettings? = null
  private var myGradleDistributionRoot: Path? = null
  private var deprecationTextLineCount = 0
  private var originalGradleUserHome: Path? = null

  protected open val isWarningsAllowed: Boolean
    get() = false

  protected val gradleUserHome: Path
    get() {
      val serviceDirectory = GradleSettings.getInstance(myProject).serviceDirectoryPath
      return if (serviceDirectory != null) Path.of(serviceDirectory) else gradleUserHomeDir(Path.of(projectPath).getEelDescriptor())
    }

  override fun getTestsTempDir(): String = "tmp"
  override fun getExternalSystemConfigFileName(): String = "build.gradle"
  override fun getCurrentExternalProjectSettings(): GradleProjectSettings = myProjectSettings!!
  override fun getExternalSystemId(): ProjectSystemId = GradleConstants.SYSTEM_ID
  protected override fun runInDispatchThread(): Boolean = false

  @Throws(Exception::class)
  override fun setUp() {
    Assume.assumeThat(gradleVersion, versionMatcherRule.matcher)

    myProjectSettings = GradleProjectSettings().withQualifiedModuleNames()

    super.setUp()

    WriteAction.runAndWait<RuntimeException> {
      configureJdkTable()
    }
    System.setProperty(ExternalSystemExecutionSettings.REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, GRADLE_DAEMON_TTL_MS.toString())
    setUpGradleVmOptions()

    maskExtensions<UnknownSdkResolver>(UnknownSdkResolver.EP_NAME, listOf(TestUnknownSdkResolver), myTestDisposable)
    setRegistryPropertyForTest("unknown.sdk.auto", "false")
    unknownSdkFixMode = TestUnknownSdkResolver.TestUnknownSdkFixMode.REAL_LOCAL_FIX

    installGradleJvmConfigurator()
    installExecutionDeprecationChecker()
    originalGradleUserHome = this.gradleUserHome
  }

  @Throws(Exception::class)
  override fun setUpInWriteAction() {
    super.setUpInWriteAction()
    this.gradleJdkHome = requireRealJdkHome()
    myGradleDistributionRoot = configureWrapper()
  }

  @Throws(Exception::class)
  override fun tearDown() {
    if (this.gradleJdkHome == null) {
      //super.setUpInWriteAction() wasn't called
      runAll(
        { Disposer.dispose(myTestDisposable) },
        { super.tearDown() }
      )
      return
    }

    runAll(
      {
        WriteAction.runAndWait<RuntimeException>(ThrowableRunnable {
          ProjectJdkTable.getInstance().getAllJdks()
            .forEach { ProjectJdkTable.getInstance().removeJdk(it) }
          for (sdk in removedSdks) {
            SdkConfigurationUtil.addSdk(sdk)
          }
          removedSdks.clear()
        })
      },
      {
        TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        CompilerTestUtil.deleteBuildSystemDirectory(myProject)
      },
      { deprecationError.set(null) },
      { tearDownGradleVmOptions() },
      { resetGradleUserHomeIfNeeded() },
      { Disposer.dispose(myTestDisposable) },
      { super.tearDown() }
    )
  }

  override fun collectAllowedRoots(roots: MutableList<String>) {
    super.collectAllowedRoots(roots)
    roots.add(this.gradleJdkHome!!)
    roots.addAll(collectRootsInside(this.gradleJdkHome!!))
    roots.add(PathManager.getConfigPath())
    roots.add(myGradleDistributionRoot.toString())
    roots.add(gradleUserHome.toString())
    val javaHome = getVariable("JAVA_HOME")
    if (javaHome != null) roots.add(javaHome)
  }

  override fun createImportSpec(): ImportSpec {
    val importSpecBuilder = ImportSpecBuilder(super.createImportSpec())
    importSpecBuilder.withArguments("--stacktrace")
    return importSpecBuilder.build()
  }

  @Throws(IOException::class)
  override fun importProject(config: String, skipIndexing: Boolean?) {
    var config = config
    if (IS_UNDER_TEAMCITY) {
      config = injectRepo(config)
    }
    super.importProject(config, skipIndexing)
    handleDeprecationError(deprecationError.get())
  }

  override fun handleImportFailure(errorMessage: String, errorDetails: String?) {
    val combinedMessage = errorMessage + "\n" + errorDetails
    if (combinedMessage.contains("org.gradle.wrapper.Download.download") && combinedMessage.contains("java.net.SocketException")) {
      reportUnavailability<Any>("Gradle distribution service", null)
    }
    super.handleImportFailure(errorMessage, errorDetails)
  }

  @Throws(IOException::class)
  open fun importProject(config: @NonNls String) {
    importProject(config, null)
  }

  open fun requireJdkHome(): String {
    return requireJdkHome(this.currentGradleVersion, myTargetJavaVersionWatcher.restriction)
  }

  protected open fun configureGradleVmOptions(options: MutableSet<String>) {
    if (isGradleAtLeast("7.0") && !this.isWarningsAllowed) {
      options.add("-Dorg.gradle.warning.mode=fail")
    }
  }

  protected open fun installGradleJvmConfigurator() {
    ExternalSystemApiUtil.subscribe(myProject, GradleConstants.SYSTEM_ID, object : ExternalSystemSettingsListener<GradleProjectSettings> {
      override fun onProjectsLinked(settings: MutableCollection<GradleProjectSettings>) {
        for (projectSettings in settings) {
          projectSettings.gradleJvm = GRADLE_JDK_NAME
        }
      }
    }, myTestDisposable)
  }

  protected open fun configureJdkTable() {
    cleanJdkTable()
    populateJdkTable(mutableListOf(createJdkFromJavaHome()))
  }

  protected open fun handleDeprecationError(errorInfo: Couple<String>?) {
    if (errorInfo == null) return
    handleImportFailure(errorInfo.first!!, errorInfo.second)
  }

  protected open fun injectRepo(config: @NonNls String): String {
    val mavenRepositoryPatch =
      """
        allprojects {
            repositories {
                maven {
                    url = 'https://repo.labs.intellij.net/repo1'
                }
            }
        }
        
        """.trimIndent()
    if (config.contains(MAVEN_REPOSITORY_PATCH_PLACE)) {
      return config.replace(MAVEN_REPOSITORY_PATCH_PLACE, mavenRepositoryPatch)
    }
    else {
      return mavenRepositoryPatch + config
    }
  }

  fun script(configure: Consumer<TestGradleBuildScriptBuilder>): String {
    val builder = createBuildScriptBuilder()
    configure.accept(builder)
    return builder.generate()
  }

  fun settingsScript(configure: Consumer<GradleSettingScriptBuilder<*>>): String {
    val builder = create(this.currentGradleVersion, GradleDsl.GROOVY)
    configure.accept(builder)
    return builder.generate()
  }

  protected fun cleanJdkTable() {
    removedSdks.clear()
    for (sdk in ProjectJdkTable.getInstance().getAllJdks()) {
      ProjectJdkTable.getInstance().removeJdk(sdk)
      if (GRADLE_JDK_NAME == sdk.getName()) continue
      removedSdks.add(sdk)
    }
  }

  protected fun populateJdkTable(jdks: List<Sdk>) {
    for (jdk in jdks) {
      ProjectJdkTable.getInstance().addJdk(jdk)
    }
  }

  protected fun importProjectUsingSingeModulePerGradleProject() {
    currentExternalProjectSettings.isResolveModulePerSourceSet = false
    importProject()
  }

  @Throws(IOException::class)
  protected fun importProjectUsingSingeModulePerGradleProject(config: @NonNls String) {
    currentExternalProjectSettings.isResolveModulePerSourceSet = false
    importProject(config)
  }

  protected fun createBuildScriptBuilder(): TestGradleBuildScriptBuilder = TestGradleBuildScriptBuilder(this.currentGradleVersion)
    .addPrefix(MAVEN_REPOSITORY_PATCH_PLACE, "")

  protected fun createEmptyGradleRunConfiguration(name: String): GradleRunConfiguration {
    val runManager = RunManagerEx.getInstanceEx(myProject)
    val settings = runManager.createConfiguration(name, GradleExternalTaskConfigurationType::class.java)
    return settings.getConfiguration() as GradleRunConfiguration
  }

  @Throws(IOException::class)
  protected fun createSettingsFile(content: @NonNls String): VirtualFile = createProjectSubFile("settings.gradle", content)

  /**
   * Produces settings content and creates necessary directories.
   * @param projects list of sub-project to create
   * @return a block of `include 'project-name'` lines for settings.gradle
   */
  protected fun including(vararg projects: String): String = including(myProjectRoot, *projects)

  protected fun including(root: VirtualFile?, vararg projects: String): String {
    assertNotNull(root)
    return TestGradleSettingsScriptHelper(root!!.toNioPath(), projects as Array<String>).build()
  }

  protected fun assertMergedModuleCompileLibDepScope(moduleName: String, depName: String) {
    assertModuleLibDepScope(moduleName, depName, DependencyScope.COMPILE)
  }

  protected fun assertMergedModuleCompileModuleDepScope(moduleName: String, depName: String) {
    assertModuleModuleDepScope(moduleName, depName, DependencyScope.COMPILE)
  }

  protected fun isGradleOlderThan(ver: String): Boolean {
    return GradleVersionUtil.isGradleOlderThan(this.currentGradleBaseVersion, ver)
  }

  protected fun isGradleAtLeast(ver: String): Boolean {
    return GradleVersionUtil.isGradleAtLeast(this.currentGradleBaseVersion, ver)
  }

  protected fun convertToLibraryName(fsRoot: VirtualFile): String = "Gradle: ${fsRoot.getName()}"

  protected fun enableGradleDebugWithSuspend() {
    val settings = GradleSystemSettings.getInstance()
    val currentOptions = settings.gradleVmOptions ?: ""
    settings.gradleVmOptions = "$currentOptions -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
  }

  @Throws(IOException::class)
  protected fun overrideGradleUserHome(relativeUserHomePath: String) {
    val gradleUserHome = myTestDir.resolve(relativeUserHomePath)
    if (myGradleDistributionRoot!!.exists()) {
      val targetGradleDistribution = getGradleDistributionRoot(gradleUserHome, currentGradleVersion)
      targetGradleDistribution.createParentDirectories()
      myGradleDistributionRoot!!.copyRecursively(targetGradleDistribution)
    }
    GradleSettings.getInstance(myProject).setServiceDirectoryPath(gradleUserHome.toString())
  }

  protected fun resetGradleUserHomeIfNeeded() {
    if (originalGradleUserHome != this.gradleUserHome) {
      val normalizedOldGradleUserHome = originalGradleUserHome!!.normalize().toString()
      val canonicalOldGradleUserHome = FileUtil.toCanonicalPath(normalizedOldGradleUserHome)
      GradleSettings.getInstance(myProject).setServiceDirectoryPath(canonicalOldGradleUserHome)
    }
  }

  private fun configureWrapper(): Path {
    myProjectSettings!!.distributionType = DistributionType.DEFAULT_WRAPPED
    val projectRoot = myProjectRoot.toNioPath()
    generateGradleWrapper(projectRoot, currentGradleVersion)

    val gradleJarPath = getGradleDistributionJarPath(projectRoot)
    val gradleDistributionRootPath = gradleJarPath.parent.parent
    validateGradleJar(gradleJarPath)

    if (!gradleJarPath.exists()) {
      val localDistributionRoot = getLocalGradleDistributionRoot(currentGradleVersion)
      if (localDistributionRoot != gradleDistributionRootPath && localDistributionRoot.exists()) {
        gradleDistributionRootPath.delete(true)
        localDistributionRoot.copyRecursively(gradleDistributionRootPath)
      }
    }
    return gradleDistributionRootPath
  }

  private fun tearDownGradleVmOptions() {
    GradleSystemSettings.getInstance().gradleVmOptions = ""
  }

  private fun requireWslJdkHome(distribution: WSLDistribution): String {
    var jdkPath = System.getProperty("wsl.jdk.path")
    if (jdkPath == null) {
      jdkPath = "/usr/lib/jvm/java-11-openjdk-amd64"
    }
    return distribution.getWindowsPath(jdkPath)
  }

  private fun installExecutionDeprecationChecker() {
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    val notificationListener: ExternalSystemTaskNotificationListener = object : ExternalSystemTaskNotificationListener {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
        if (text.contains("This is scheduled to be removed in Gradle")
            || text.contains("Deprecated Gradle features were used in this build")
        ) {
          deprecationTextLineCount = 30
        }
        if (deprecationTextLineCount > 0) {
          deprecationTextBuilder.append(text)
          deprecationTextLineCount--
          if (deprecationTextLineCount == 0) {
            deprecationError.set(Couple.of("Deprecation warning from Gradle", deprecationTextBuilder.toString()))
            deprecationTextBuilder.setLength(0)
          }
        }
      }
    }
    notificationManager.addNotificationListener(notificationListener, myTestDisposable)
  }

  private fun requireRealJdkHome(): String {
    if (myWSLDistribution != null) {
      return requireWslJdkHome(myWSLDistribution!!)
    }
    return requireJdkHome()
  }

  private fun setUpGradleVmOptions() {
    val settings = GradleSystemSettings.getInstance()
    val defaultVmOptions = settings.gradleVmOptions ?: ""

    val requiredVmOptions = mutableSetOf<String>()
    configureGradleVmOptions(requiredVmOptions)

    val effectiveVmOptions = String.format("%s %s", defaultVmOptions, Strings.join(requiredVmOptions, " ")).trim { it <= ' ' }
    settings.gradleVmOptions = effectiveVmOptions
  }

  private fun createJdkFromJavaHome(): Sdk {
    val jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(gradleJdkHome!!))
    val javaSdk = JavaSdk.getInstance()
    val javaSdkType: SdkType = javaSdk ?: SimpleJavaSdkType.getInstance()
    val jdk = SdkConfigurationUtil.setupSdk(arrayOfNulls<Sdk>(0), jdkHomeDir!!, javaSdkType, true, null, GRADLE_JDK_NAME)
    assertNotNull("Cannot create JDK for " + this.gradleJdkHome, jdk)
    return jdk!!
  }

  companion object {
    const val BASE_GRADLE_VERSION: String = VersionMatcherRule.BASE_GRADLE_VERSION

    private const val GRADLE_JDK_NAME: String = "Gradle JDK"
    private const val GRADLE_DAEMON_TTL_MS = 10000
    private const val MAVEN_REPOSITORY_PATCH_PLACE = "// Place for Maven repository patch"

    @JvmStatic
    fun requireJdkHome(
      gradleVersion: GradleVersion,
      javaVersionRestriction: JavaVersionRestriction,
    ): String {
      if (isSupported(gradleVersion, currentJavaVersion()) &&
          !javaVersionRestriction.isRestricted(gradleVersion, currentJavaVersion())
      ) {
        return IdeaTestUtil.requireRealJdkHome()
      }
      // fix exception of FJP at JavaHomeFinder.suggestHomePaths => ... => EnvironmentUtil.getEnvironmentMap => CompletableFuture.<clinit>
      IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)
      return resolveGradleJvmHomePath(gradleVersion, javaVersionRestriction)
    }

    @JvmStatic
    @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
    open fun data(): Iterable<*> {
      return VersionMatcherRule.getSupportedGradleVersions()
    }
  }
}
