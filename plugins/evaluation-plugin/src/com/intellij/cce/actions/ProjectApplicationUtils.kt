package com.intellij.cce.actions

import com.intellij.conversion.ConversionListener
import com.intellij.conversion.ConversionService
import com.intellij.ide.CommandLineInspectionProgressReporter
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.ide.impl.PatchProjectUtil
import com.intellij.ide.impl.ProjectUtil.openOrImport
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.idea.maven.MavenCommandLineInspectionProjectConfigurator
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.gradle.GradleCommandLineProjectConfigurator
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object ProjectApplicationUtils {

  private val logger = LoggerFactory.getLogger(javaClass)

  /**
   * Rewritten from {@link com.intellij.codeInspection.InspectionApplicationBase}.
   * Implementation which reuse InspectionApplicationBase:
   *
   * val app = object : InspectionApplicationBase() {
   *     fun open(): Project? {
   *         return this.openProject(projectPath, parentDisposable)
   *     }
   * }
   *
   * return app.open() ?: throw ProjectApplicationException("Can not open project")
   */
  suspend fun openProject(projectPath: Path, parentDisposable: Disposable, projectToClose: Project? = null): Project {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    ApplicationManagerEx.getApplicationEx().isSaveAllowed = false

    LocalFileSystem.getInstance().refreshAndFindFileByPath(
      FileUtil.toSystemIndependentName(projectPath.toString())
    ) ?: throw RuntimeException("Project directory not found.")

    convertProject(projectPath)

    configureProjectEnvironment(projectPath)

    val project = openOrImport(projectPath, projectToClose, forceOpenInNewFrame = true)
                  ?: throw RuntimeException("Can not open or import project from $projectPath.")
    Disposer.register(parentDisposable) { closeProject(project) }

    waitAllStartupActivitiesPassed(project)

    ApplicationManager.getApplication().invokeAndWait {
      VirtualFileManager.getInstance().refreshWithoutFileWatcher(false)
    }

    ApplicationManager.getApplication().invokeAndWait {
      PatchProjectUtil.patchProject(project)
    }

    waitForInvokeLaterActivities()

    return project
  }

  private suspend fun convertProject(projectPath: Path) {
    val conversionService = ConversionService.getInstance()
                            ?: throw RuntimeException("Can not convert project $projectPath")
    val conversionResult = conversionService.convertSilently(projectPath, ConversionListenerImpl())
    if (conversionResult.openingIsCanceled()) {
      throw RuntimeException("Project opening is canceled $projectPath")
    }
  }

  private fun configureProjectEnvironment(projectPath: Path) {
    for (configurator in CommandLineInspectionProjectConfigurator.EP_NAME.extensionList) {
      val context = ConfiguratorContextImpl(projectPath)
      if (configurator.isApplicable(context)) {
        logger.info("Applying configurator ${configurator.name} to configure project environment $projectPath.")
        configurator.configureEnvironment(context)
      }
    }
  }

  fun resolveProject(
    project: Project,
    configurator: CommandLineInspectionProjectConfigurator,
    context: ConfiguratorContext
  ) {
    logger.info("Resolving project ${project.name}...")
    logger.info("Applying configurator ${configurator.name} to resolve project ${project.name}...")
    configurator.preConfigureProject(project, context)
    configurator.configureProject(project, context)
    waitForInvokeLaterActivities()
    logger.info("Project ${project.name} was successfully resolved with configurator ${configurator.name}!")
  }

  private fun closeProject(project: Project) {
    logger.info("Closing project $project...")
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    ApplicationManager.getApplication().invokeAndWait {
      ProjectManagerEx.getInstanceEx().forceCloseProject(project)
    }
  }

  private suspend fun waitAllStartupActivitiesPassed(project: Project): Unit = suspendCoroutine {
    logger.info("Waiting all startup activities passed $project...")
    StartupManager.getInstance(project).runAfterOpened { it.resume(Unit) }
    waitForInvokeLaterActivities()
  }

  /**
   * Magic loop is used to wait all invoke later activities passed.
   * No loop coses empty analyzer output as some project opening activities are not finished yet.
   */
  private fun waitForInvokeLaterActivities() {
    logger.info("Waiting all invoked later activities...")
    repeat(10) {
      ApplicationManager.getApplication().invokeAndWait({}, ModalityState.any())
    }
  }
}


class ConversionListenerImpl : ConversionListener {
  private val logger = LoggerFactory.getLogger(javaClass)

  override fun conversionNeeded() {
    logger.info("Conversion is needed for project.")
  }

  override fun successfullyConverted(backupDir: Path) {
    logger.info("Project successfully converted.")
  }

  override fun error(message: String) {
    throw RuntimeException(message)
  }

  override fun cannotWriteToFiles(readonlyFiles: List<Path>) {
    throw RuntimeException("Can not write to files ${readonlyFiles.joinToString { it.fileName.toString() }}")
  }
}

class ConfiguratorContextImpl(
  private val projectRoot: Path,
  private val indicator: ProgressIndicatorBase = ProgressIndicatorBase(),
  private val filesFilter: Predicate<Path> = Predicate { true },
  private val virtualFilesFilter: Predicate<VirtualFile> = Predicate { true }
) : ConfiguratorContext {
  private val logger = LoggerFactory.getLogger(javaClass)
  override fun getProgressIndicator() = indicator
  override fun getLogger() = object : CommandLineInspectionProgressReporter {
    override fun reportError(message: String) {
      logger.warn("ERROR: $message")
    }

    override fun reportMessage(minVerboseLevel: Int, message: String) {
      logger.info("PROGRESS: $message")
    }
  }

  override fun getProjectPath() = projectRoot
  override fun getFilesFilter(): Predicate<Path> = filesFilter
  override fun getVirtualFilesFilter(): Predicate<VirtualFile> = virtualFilesFilter
}

class JvmProjectResolver {
  private val logger = LoggerFactory.getLogger(javaClass)

  fun resolveProject(project: Project) {
    logger.info("Started to resolve project ${project.name}.")
    val configurator = getProjectConfigurator(project)
    val projectPath = project.basePath?.let { Path.of(it) }
                      ?: throw RuntimeException("Undefined base path for project ${project.name}")
    val context = ConfiguratorContextImpl(projectPath)

    ProjectApplicationUtils.resolveProject(project, configurator, context)
  }

  private fun getProjectConfigurator(project: Project): CommandLineInspectionProjectConfigurator {
    return if (MavenProjectsManager.getInstance(project).isMavenizedProject) {
      logger.info("Project ${project.name} considered to be maven")
      MavenCommandLineInspectionProjectConfigurator()
    }
    else {
      logger.info("Project ${project.name} considered to be gradle")
      GradleCommandLineProjectConfigurator()
    }
  }
}
