package com.intellij.warmup.util

import com.intellij.conversion.ConversionListener
import com.intellij.conversion.ConversionService
import com.intellij.ide.CommandLineInspectionProgressReporter
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.PatchProjectUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate

private val LOG = ConsoleLog

fun importOrOpenProject(args: OpenProjectArgs, indicator: ProgressIndicator): Project = runAndCatchNotNull("Opening Project") {
  LOG.info("Opening project from ${args.projectDir}...")
  var project: Project? = null
  val action = Runnable {
    indicator.pushState()
    try {
      project = runBlocking {
        runTaskAndLogTime("open project") {
          importOrOpenProjectImpl(args, indicator)
        }
      }
    }
    catch (t: Throwable) {
      LOG.error("Failed to open project ${args.projectDir}. ${t.message}", t)
    }
    finally {
      indicator.popState()
    }
  }

  //most of the sensible operations would run in the same thread
  ProgressManager.getInstance().runProcess(action, indicator)

  project
}

private suspend fun importOrOpenProjectImpl(args: OpenProjectArgs, indicator: ProgressIndicator): Project {
  val vfsProject = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(args.projectDir)
                   ?: throw RuntimeException("Project path ${args.projectDir} is not found")

  runTaskAndLogTime("refresh VFS") {
    LOG.info("Refreshing VFS ${args.projectDir}...")
    VfsUtil.markDirtyAndRefresh(false, true, true, args.projectDir.toFile())
  }
  yieldThroughInvokeLater()

  callProjectConversion(args)

  callProjectConfigurators(args, indicator) {
    this.configureEnvironment(it)
  }

  val project = runTaskAndLogTime("open project") {
    ProjectUtil.openOrImport(vfsProject.toNioPath(), OpenProjectTask())
  } ?: throw RuntimeException("Failed to open project, null is returned")
  yieldThroughInvokeLater()

  runTaskAndLogTime("patching project") {
    invokeAndWaitIfNeeded {
      //TODO[jo]: allow to configure that from commandline parameters
      PatchProjectUtil.patchProject(project)
    }
  }

  yieldAndWaitForDumbModeEnd(project)

  callProjectConfigurators(args, indicator) {
    this.configureProject(project, it)

    //the configuration may add more dumb tasks to complete
    //we flush the queue to avoid a deadlock between a modal progress & invokeLater
    yieldAndWaitForDumbModeEnd(project)
  }

  LOG.info("Finishing project open...")
  val modulesCount = runReadAction {
    val modules = ModuleManager.getInstance(project).modules
    if (modules.isEmpty()) {
      LOG.warn("Project contains 0 modules. There is nothing to index")
    }

    modules.size
  }

  runTaskAndLogTime("check project roots") {
    val errors = TreeSet<String>()
    val missingSDKs = TreeSet<String>()
    runReadAction {
      ProjectRootManager.getInstance(project).contentRoots.forEach { file ->
        if (!file.exists()) {
          errors += "Missing root: $file"
        }
      }

      ProjectRootManager.getInstance(project).orderEntries().forEach { root ->
        OrderRootType.getAllTypes().flatMap { root.getFiles(it).toList() }.forEach { file ->
          if (!file.exists()) {
            errors += "Missing root: $file for ${root.ownerModule.name} for ${root.presentableName}"
          }
        }

        if (root is JdkOrderEntry && root.jdk == null) {
          root.jdkName?.let { missingSDKs += it }
        }

        true
      }
    }

    errors += missingSDKs.map { "Missing JDK entry: ${it}" }
    errors.forEach { LOG.warn(it) }
  }

  LOG.info("Project with $modulesCount module(s) is ready for the import")
  return project
}

private val listener = object : ConversionListener, CommandLineInspectionProgressReporter {
  override fun reportError(message: String) {
    LOG.warn("PROGRESS: $message")
  }

  override fun reportMessage(minVerboseLevel: Int, message: String) {
    LOG.info("PROGRESS: $message")
  }

  override fun error(message: String) {
    LOG.warn("PROGRESS: $message")
  }

  override fun conversionNeeded() {
    LOG.info("PROGRESS: Project conversion is needed")
  }

  override fun successfullyConverted(backupDir: Path) {
    LOG.info("PROGRESS: Project was successfully converted")
  }

  override fun cannotWriteToFiles(readonlyFiles: List<Path>) {
    LOG.info("PROGRESS: Project conversion failed for:\n" + readonlyFiles.joinToString("\n"))
  }
}

private suspend fun callProjectConversion(projectArgs: OpenProjectArgs) {
  if (!projectArgs.convertProject) {
    return
  }

  val conversionService = ConversionService.getInstance() ?: return
  runTaskAndLogTime("convert project") {
    LOG.info("Checking if conversions are needed for the project")
    val conversionResult = invokeAndWaitIfNeeded {
      conversionService.convertSilently(projectArgs.projectDir, listener)
    }

    if (conversionResult.openingIsCanceled()) {
      throw RuntimeException("Failed to run project conversions before open")
    }

    if (conversionResult.conversionNotNeeded()) {
      LOG.info("No conversions were needed")
    }
  }

  yieldThroughInvokeLater()
}

private suspend fun callProjectConfigurators(
  projectArgs: OpenProjectArgs,
  indicator: ProgressIndicator,
  action: suspend CommandLineInspectionProjectConfigurator.(ConfiguratorContext) -> Unit
) {

  if (!projectArgs.configureProject) return
  CommandLineInspectionProjectConfigurator.EP_NAME.extensionList.forEach { configurator ->
    indicator.pushState()
    try {
      val context = object : ConfiguratorContext {
        override fun getProgressIndicator() = indicator
        override fun getLogger() = listener
        override fun getProjectPath() = projectArgs.projectDir
        override fun getFilesFilter(): Predicate<Path> = Predicate { true }
        override fun getVirtualFilesFilter(): Predicate<VirtualFile> = Predicate { true }
      }

      if (configurator.name in projectArgs.disabledConfigurators) {
        listener.reportMessage(1, "Configurator ${configurator.name} is disabled in the settings")
      }
      else if (configurator.isApplicable(context)) {
        runTaskAndLogTime("configure " + configurator.name) {
          action(configurator, context)
        }
      }
    }
    finally {
      indicator.popState()
    }
  }

  yieldThroughInvokeLater()
}
