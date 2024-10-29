package com.intellij.remoteDev.downloader

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CustomConfigMigrationOption
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.bootstrap.RuntimeModuleIntrospection
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.remoteDev.util.ProductInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.JavaModuleOptions
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.intellij.util.system.OS
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.io.path.exists

@ApiStatus.Internal
class EmbeddedClientLauncher private constructor(private val moduleRepository: RuntimeModuleRepository, 
                                                 private val moduleRepositoryPath: Path) {
  companion object {
    private const val USE_CUSTOM_PATHS_PROPERTY = "rdct.embedded.client.use.custom.paths"
    private val LOG = logger<EmbeddedClientLauncher>()
    
    fun create(): EmbeddedClientLauncher? {
      val moduleRepository = RuntimeModuleIntrospection.moduleRepository ?: return null
      val moduleRepositoryPath = RuntimeModuleIntrospection.moduleRepositoryPath ?: return null
      try {
        moduleRepository.getModule(getRootFrontendModule())
      }
      catch (e: Exception) {
        LOG.warn("Failed to load embedded client: " + e.message)
        return null
      }
      return EmbeddedClientLauncher(moduleRepository, moduleRepositoryPath)
    }

    private fun getRootFrontendModule(): RuntimeModuleId = getRootFrontendModuleForIde(PlatformUtils.getPlatformPrefix())

    /**
     * Returns root module for the frontend part of the IDE with the given [platformPrefix].
     * This method is supposed to be used when running from sources and tests only. In production, the embedded client is started via
     * its own launcher included in the distribution, where the correct module is set by the build scripts.
     */
    @VisibleForTesting
    fun getRootFrontendModuleForIde(platformPrefix: String): RuntimeModuleId = when (platformPrefix) {
      PlatformUtils.IDEA_PREFIX, PlatformUtils.IDEA_CE_PREFIX -> RuntimeModuleId.module("intellij.idea.frontend")
      PlatformUtils.IDEA_EDU_PREFIX -> RuntimeModuleId.module("intellij.edu.remote.frontend")
      PlatformUtils.PYCHARM_PREFIX, PlatformUtils.PYCHARM_CE_PREFIX -> RuntimeModuleId.module("intellij.pycharm.frontend")
      PlatformUtils.RIDER_PREFIX -> RuntimeModuleId.module("intellij.rider.frontend")
      PlatformUtils.GOIDE_PREFIX -> RuntimeModuleId.module("intellij.goland.frontend")
      PlatformUtils.CLION_PREFIX -> RuntimeModuleId.module("intellij.clion.ide.frontend")
      PlatformUtils.PHP_PREFIX -> RuntimeModuleId.module("intellij.phpstorm.frontend")
      PlatformUtils.WEB_PREFIX -> RuntimeModuleId.module("intellij.webstorm.frontend")
      PlatformUtils.RUBY_PREFIX -> RuntimeModuleId.module("intellij.rubymine.frontend")
      PlatformUtils.RUSTROVER_PREFIX -> RuntimeModuleId.module("intellij.rustrover.frontend")
      else -> RuntimeModuleId.module("intellij.platform.frontend.split")
    }

    fun isThinClientCustomCommand(customCommandData: ProductInfo.CustomCommandLaunchData): Boolean {
      return customCommandData.commands.contains("thinClient")
    }
  }

  fun launch(urlToOpen: String, lifetime: Lifetime, errorReporter: EmbeddedClientErrorReporter): Lifetime {
    return launch(urlToOpen, emptyList(), lifetime, errorReporter)
  }

  fun launch(url: String, extraArguments: List<String>, lifetime: Lifetime, errorReporter: EmbeddedClientErrorReporter): Lifetime {
    val launcherData = createLauncherViaIdeExecutable() ?: findOldJetBrainsClientLauncher()
    if (launcherData != null) {
      LOG.debug("Start embedded client using launcher")
      val workingDirectory = Path(PathManager.getHomePath())
      return CodeWithMeClientDownloader.runJetBrainsClientProcess(
        launcherData,
        workingDirectory,
        clientBuild = ApplicationInfo.getInstance().build.withoutProductCode(),
        url,
        extraArguments,
        lifetime
      )
    }

    val processLifetimeDef = lifetime.createNested()

    val arguments = listOf("thinClient", url) + extraArguments
    val javaParameters = createProcessParameters(moduleRepository, moduleRepositoryPath, arguments)
    val commandLine = javaParameters.toCommandLine()
    LOG.debug { "Starting embedded client: $commandLine" }
    val handler = OSProcessHandler.Silent(commandLine)
    val output = Collections.synchronizedList(ArrayList<@NlsSafe String>())
    handler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        output.add(event.text)
      }

      override fun processTerminated(event: ProcessEvent) {
        if (event.exitCode != 0) {
          errorReporter.startupFailed(event.exitCode, output)
        }
        ApplicationManager.getApplication().invokeLater { 
          processLifetimeDef.terminate()
        }
      }
    })

    handler.startNotify()
    return processLifetimeDef
  }

  private fun createLauncherViaIdeExecutable(): JetBrainsClientLauncherData? {
    val ideHome = Path(PathManager.getHomePath())
    val productInfoPath = if (SystemInfo.isMac) ApplicationEx.PRODUCT_INFO_FILE_NAME_MAC else ApplicationEx.PRODUCT_INFO_FILE_NAME
    val productInfoData = CodeWithMeClientDownloader.parseProductInfo(ideHome.resolve(productInfoPath)) ?: return null
    val frontendLaunchData = productInfoData.launch.find { launchData ->
      launchData.customCommands.orEmpty().any { isThinClientCustomCommand(it) }
    }
    if (frontendLaunchData == null) {
      LOG.info("Cannot use IDE's launcher because product info under $ideHome doesn't have special handling for 'thinClient' command")
      return null
    }

    if (SystemInfo.isMac) {
      val appPath = ideHome.parent
      if (appPath != null && appPath.name.endsWith(".app")) {
        CodeWithMeClientDownloader.createLauncherDataForMacOs(appPath)
      }
      else {
        LOG.info("Cannot use launcher because $ideHome doesn't look like a path with installation")
        null
      }
    }
    val executable = ideHome.resolve(frontendLaunchData.launcherPath)
    if (!executable.exists()) {
      LOG.warn("Cannot use IDE's launcher because $executable does not exist")
      return null
    }
    return JetBrainsClientLauncherData(executable, listOf(executable.pathString))
  }
  
  private fun findOldJetBrainsClientLauncher(): JetBrainsClientLauncherData? {
    return when (OS.CURRENT) {
      OS.macOS -> {
        return null
      }
      OS.Windows -> PathManager.findBinFile("jetbrains_client64.exe")?.let { 
        JetBrainsClientLauncherData(it, listOf(it.pathString))
      }
      else -> PathManager.findBinFile("jetbrains_client.sh")?.let {
        JetBrainsClientLauncherData(it, listOf(it.pathString))
      }
    }
  }

  private fun createProcessParameters(moduleRepository: RuntimeModuleRepository, moduleRepositoryPath: Path, arguments: List<String>): SimpleJavaParameters {
    val javaParameters = SimpleJavaParameters()
    javaParameters.jdk = SimpleJavaSdkType.getInstance().createJdk("", SystemProperties.getJavaHome())
    javaParameters.setShortenCommandLine(ShortenCommandLine.ARGS_FILE)
    val customIdeaPropertiesPath = EnvironmentUtil.getValue("JETBRAINS_CLIENT_PROPERTIES")
    if (customIdeaPropertiesPath != null) {
      javaParameters.vmParametersList.addProperty(PathManager.PROPERTIES_FILE, customIdeaPropertiesPath)
    }
    else if (ApplicationManager.getApplication().isUnitTestMode || SystemProperties.getBooleanProperty(USE_CUSTOM_PATHS_PROPERTY, false)) {
      val tempDir = Path(PathManager.getTempPath()) / "embedded-client"
      val configDir = tempDir / "config"
      if (!configDir.exists()) {
        CustomConfigMigrationOption.MigrateFromCustomPlace(PathManager.getConfigDir()).writeConfigMarkerFile(configDir)
      }
      javaParameters.vmParametersList.defineProperty(PathManager.PROPERTY_CONFIG_PATH, configDir.pathString)
      val systemDir = tempDir / "system"
      javaParameters.vmParametersList.defineProperty(PathManager.PROPERTY_SYSTEM_PATH, systemDir.pathString)
      val logDir = PathManager.getLogDir() / "embedded-client"
      javaParameters.vmParametersList.defineProperty(PathManager.PROPERTY_LOG_PATH, logDir.pathString)
    }
    passProperties(javaParameters.vmParametersList)
    javaParameters.mainClass = "com.intellij.platform.runtime.loader.IntellijLoader"
    val runtimeLoaderModule = RuntimeModuleId.module("intellij.platform.runtime.loader")
    javaParameters.classPath.addAllFiles(moduleRepository.getModule(runtimeLoaderModule).moduleClasspath.map { it.toFile() })
    addVmOptions(javaParameters.vmParametersList, moduleRepositoryPath)
    javaParameters.programParametersList.addAll(arguments)
    return javaParameters
  }

  private fun passProperties(vmParametersList: ParametersList) {
    val propertiesToPass = listOf(
      "jna.boot.library.path", 
      "pty4j.preferred.native.folder", 
      "jna.nosys", 
      "jna.noclasspath", 
      "idea.is.internal",
    )
    propertiesToPass.forEach { 
      vmParametersList.defineProperty(it, System.getProperty(it))
    }
  }

  private fun addVmOptions(vmParametersList: ParametersList, moduleRepositoryPath: Path) {
    val vmOptionsFile = PathManager.getConfigDir() / "embedded-client" / "jetbrains_client64.vmoptions"
    val customizableOptions: List<String>
    if (vmOptionsFile.exists()) {
      customizableOptions = vmOptionsFile.readLines().mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() } }
    }
    else {
      customizableOptions = getDefaultCustomizableVmOptions()
      vmOptionsFile.createParentDirectories()
      vmOptionsFile.writeLines(customizableOptions)
    }

    vmParametersList.addAll(customizableOptions)

    val build = ApplicationInfo.getInstance().build
    val jetBrainsClientOptions = listOf(
      "-Djb.vmOptionsFile=${vmOptionsFile.pathString}",
      "-Didea.vendor.name=JetBrains",
      "-Didea.paths.selector=${PathManager.getPathsSelector()}",
      "-Didea.platform.prefix=JetBrainsClient",
      "-Dide.no.platform.update=true",
      "-Didea.initially.ask.config=never",
      "-Dnosplash=true",
      "-Didea.paths.customizer=com.intellij.platform.ide.impl.startup.multiProcess.FrontendProcessPathCustomizer",
      "-Dintellij.platform.runtime.repository.path=${moduleRepositoryPath.pathString}",
      "-Dintellij.platform.root.module=${getRootFrontendModule().stringId}",
      "-Dintellij.platform.product.mode=${ProductMode.FRONTEND.id}",
      "-Dintellij.platform.full.ide.product.code=${build.productCode}",
      "-Dintellij.platform.load.app.info.from.resources=true",
      "-Dsplash=true",
    )
    vmParametersList.addAll(jetBrainsClientOptions)
    if (SystemInfo.isMac) {
      vmParametersList.add("-Dapple.awt.application.name=JetBrains Client")
    }
    
    val openedPackagesStream = EmbeddedClientLauncher::class.java.getResourceAsStream("/META-INF/OpenedPackages.txt")
                               ?: error("Cannot load OpenedPackages.txt")
    val addOpensOptions = openedPackagesStream.use {
      JavaModuleOptions.readOptions(it, OS.CURRENT)
    }
    vmParametersList.addAll(addOpensOptions)
    
    val debugPort = Registry.get("rdct.embedded.client.debug.port").asInteger()
    if (debugPort > 0) {
      val suspend = if (Registry.get("rdct.embedded.client.debug.suspend").asBoolean()) "y" else "n"
      vmParametersList.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=$debugPort")
    }
  }

  private fun getDefaultCustomizableVmOptions(): List<String> {
    //todo reuse options instead of duplicating them here: IJPL-225

    val memoryOptions = listOf(
      "-Xms128m",
      "-Xmx1500m",
      "-XX:ReservedCodeCacheSize=512m",
    )

    //duplicates VmOptionsGenerator
    val commonOptions = listOf(
      "-XX:+UseG1GC",
      "-XX:SoftRefLRUPolicyMSPerMB=50",
      "-XX:CICompilerCount=2",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-XX:-OmitStackTraceInFastThrow",
      "-XX:+IgnoreUnrecognizedVMOptions",
      "-XX:CompileCommand=exclude,com/intellij/openapi/vfs/impl/FilePartNodeRoot,trieDescend",
      "-XX:MaxJavaStackTraceDepth=10000",
      "-ea",
      "-Dsun.io.useCanonCaches=false",
      "-Dsun.java2d.metal=true",
      "-Djbr.catch.SIGABRT=true",
      "-Djdk.http.auth.tunneling.disabledSchemes=\"\"",
      "-Djdk.attach.allowAttachSelf=true",
      "-Djdk.module.illegalAccess.silent=true",
      "-Dkotlinx.coroutines.debug=off",
      "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
    )

    val osSpecificOptions = when (OS.CURRENT) {
      OS.Linux -> listOf(
        //LinuxDistributionBuilder::generateVMOptions
        "-Dsun.tools.attach.tmp.only=true",
        "-Dawt.lock.fair=true",
      )
      OS.macOS -> listOf("-Dapple.awt.application.appearance=system") //MacDistributionBuilder.layoutMacApp
      else -> emptyList()
    }
    return memoryOptions + commonOptions + osSpecificOptions
  }
}