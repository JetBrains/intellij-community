package com.intellij.remoteDev.downloader

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CustomConfigMigrationOption
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.bootstrap.RuntimeModuleIntrospection
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.JavaModuleOptions
import com.intellij.util.SystemProperties
import com.intellij.util.system.OS
import com.jetbrains.rd.util.lifetime.Lifetime
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString

internal class EmbeddedClientLauncher private constructor(private val moduleRepository: RuntimeModuleRepository, 
                                                 private val moduleRepositoryPath: Path) {
  companion object {
    fun create(): EmbeddedClientLauncher? {
      val moduleRepository = RuntimeModuleIntrospection.moduleRepository ?: return null
      val moduleRepositoryPath = RuntimeModuleIntrospection.moduleRepositoryPath ?: return null
      return EmbeddedClientLauncher(moduleRepository, moduleRepositoryPath)
    }
  }
  
  fun launch(urlToOpen: String, lifetime: Lifetime, project: Project?): Lifetime {
    val processLifetimeDef = lifetime.createNested()
    
    val javaParameters = createProcessParameters(moduleRepository, moduleRepositoryPath, urlToOpen)
    val handler = javaParameters.createOSProcessHandler()
    val output = Collections.synchronizedList(ArrayList<@NlsSafe String>())
    handler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        output.add(event.text)
      }

      override fun processTerminated(event: ProcessEvent) {
        if (event.exitCode != 0) {
          val notification = Notification(
            "IDE-errors",
            RemoteDevUtilBundle.message("notification.title.failed.to.start.client"),
            RemoteDevUtilBundle.message("notification.content.process.finished.with.exit.code.0", event.exitCode),
            NotificationType.ERROR
          )
          if (project != null) {
            notification.addAction(
              NotificationAction.createSimple(RemoteDevUtilBundle.message("action.notification.view.output")) {
                FileEditorManager.getInstance(project).openFile(LightVirtualFile("output.txt", output.joinToString("")), true)
              }
            )
          }
          notification.notify(project)
        }
        ApplicationManager.getApplication().invokeLater { 
          processLifetimeDef.terminate()
        }
      }
    })

    handler.startNotify()
    return processLifetimeDef
  }

  private fun createProcessParameters(moduleRepository: RuntimeModuleRepository, moduleRepositoryPath: Path, urlToOpen: String): SimpleJavaParameters {
    val javaParameters = SimpleJavaParameters()
    javaParameters.jdk = SimpleJavaSdkType.getInstance().createJdk("", SystemProperties.getJavaHome())
    javaParameters.setShortenCommandLine(ShortenCommandLine.ARGS_FILE)
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
    passProperties(javaParameters.vmParametersList)
    javaParameters.mainClass = "com.intellij.platform.runtime.loader.IntellijLoader"
    val runtimeLoaderModule = RuntimeModuleId.module("intellij.platform.runtime.loader")
    javaParameters.classPath.addAllFiles(moduleRepository.getModule(runtimeLoaderModule).moduleClasspath.map { it.toFile() })
    addVmOptions(javaParameters.vmParametersList, moduleRepositoryPath)
    javaParameters.programParametersList.add("thinClient")
    javaParameters.programParametersList.add(urlToOpen)
    return javaParameters
  }

  private fun passProperties(vmParametersList: ParametersList) {
    val propertiesToPass = listOf(
      "jna.boot.library.path", 
      "pty4j.preferred.native.folder", 
      "jna.nosys", 
      "jna.noclasspath", 
    )
    propertiesToPass.forEach { 
      vmParametersList.defineProperty(it, System.getProperty(it))
    }
  }

  private fun addVmOptions(vmParametersList: ParametersList, moduleRepositoryPath: Path) {
    //todo reuse options instead of duplicating them here: IJPL-225
    
    val memoryOptions = listOf(
      "-Xms128m",
      "-Xmx1500m",
      "-XX:ReservedCodeCacheSize=512m",
    )  
    vmParametersList.addAll(memoryOptions)
    
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
    vmParametersList.addAll(commonOptions)
    
    val osSpecificOptions = when (OS.CURRENT) {
      OS.Linux -> listOf(//LinuxDistributionBuilder::generateVMOptions
        "-Dsun.tools.attach.tmp.only=true",
        "-Dawt.lock.fair=true",
      )
      OS.macOS -> listOf("-Dapple.awt.application.appearance=system") //MacDistributionBuilder.layoutMacApp
      else -> emptyList()
    }    
    vmParametersList.addAll(osSpecificOptions)
    
    val jetBrainsClientOptions = listOf(
      "-Didea.vendor.name=JetBrains",
      "-Didea.paths.selector=JetBrainsClient${ApplicationInfo.getInstance().build.withoutProductCode().asString()}",
      "-Didea.platform.prefix=JetBrainsClient",
      "-Dide.no.platform.update=true",
      "-Didea.initially.ask.config=never",
      "-Didea.paths.customizer=com.intellij.platform.ide.impl.startup.multiProcess.PerProcessPathCustomizer",
      "-Dintellij.platform.runtime.repository.path=${moduleRepositoryPath.pathString}",
      "-Dintellij.platform.root.module=intellij.cwm.guest",
      "-Dintellij.platform.load.app.info.from.resources=true",
      "-Dsplash=true",
    )
    vmParametersList.addAll(jetBrainsClientOptions)
    
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
}