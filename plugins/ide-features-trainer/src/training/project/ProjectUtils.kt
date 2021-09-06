// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.project

import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.setTrusted
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.NOTIFICATIONS_SILENT_MODE
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.LearnBundle
import training.util.featureTrainerVersion
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name

object ProjectUtils {
  private const val LEARNING_PROJECT_MODIFICATION = "LEARNING_PROJECT_MODIFICATION"
  private const val FEATURE_TRAINER_VERSION = "feature-trainer-version.txt"
  private val LOG = logger<ProjectUtils>()

  val learningProjectsPath: Path
    get() = Paths.get(PathManager.getSystemPath(), "demo")

  /**
   * For example:
   * @projectPath = "/learnProjects/SimpleProject"
   * @projectName = "SimpleProject"
   *
   */
  fun importOrOpenProject(langSupport: LangSupport, projectToClose: Project?, postInitCallback: (learnProject: Project) -> Unit) {
    runBackgroundableTask(LearnBundle.message("learn.project.initializing.process"), project = projectToClose) {
      val dest = getLearningInstallationContentRoot(langSupport) ?: return@runBackgroundableTask

      if (!isSameVersion(versionFile(dest))) {
        if (dest.exists()) {
          dest.delete()
        }
        langSupport.installAndOpenLearningProject(dest, projectToClose) {
          it.basePath?.let { path ->
            copyLearnProjectIcon(File(path))
          }
          postInitCallback(it)
        }
      }
      else {
        val path = langSupport.getLearningProjectPath(dest).toAbsolutePath().toString()
        LangManager.getInstance().setLearningProjectPath(langSupport, path)
        openOrImportLearningProject(dest, OpenProjectTask(projectToClose = projectToClose), langSupport, postInitCallback)
      }
    }
  }

  private fun getLearningInstallationContentRoot(langSupport: LangSupport): Path? {
    val storedProjectPath = LangManager.getInstance().getLearningProjectPath(langSupport)
    val path = if (storedProjectPath != null) langSupport.getContentRootPath(Paths.get(storedProjectPath)) else null
    val canonicalPlace = learningProjectsPath.resolve(langSupport.contentRootDirectoryName)

    var useCanonical = true

    if (path != null) {
      if (path != canonicalPlace && path.isDirectory() && versionFile(path).exists()) {
        // Learning project was already installed to some directory
        if (createProjectDirectory(canonicalPlace)) {
          // Remove the old learning directory
          val rpProvider = RecentProjectListActionProvider.getInstance()
          val projectActions = rpProvider.getActions()
          for (action in projectActions) {
            val projectPath = (action as? ReopenProjectAction)?.projectPath
            if (projectPath != null && Paths.get(projectPath) == path) {
              RecentProjectsManager.getInstance().removePath(projectPath)
            }
          }
          path.delete(recursively = true)
        }
        else {
          useCanonical = false
        }
      }
    }

    return if (useCanonical) {
      if (createProjectDirectory(canonicalPlace))
        canonicalPlace
      else invokeAndWaitIfNeeded {
        chooseParentDirectoryForLearningProject(langSupport)
      } ?: return null
    }
    else path!!
  }

  private fun createProjectDirectory(place: Path): Boolean {
    if (place.isDirectory()) return true
    try {
      place.createDirectories()
    }
    catch (e: IOException) {
      return false
    }
    return true
  }

  fun getProjectRoot(project: Project): VirtualFile {
    val roots = ProjectRootManager.getInstance(project).contentRoots
    if (roots.isNotEmpty()) {
      if (roots.size > 1) LOG.warn("Multiple content roots in project ${project.name}: ${roots.toList()}")
      return roots[0]
    }
    LOG.error("Not found content roots in project ${project.name}. " +
              "Base path: ${project.basePath}, project file path: ${project.projectFilePath}")
    throw error("Not found content roots for project")
  }

  fun simpleInstallAndOpenLearningProject(contentRoot: Path,
                                          langSupport: LangSupport,
                                          openProjectTask: OpenProjectTask,
                                          postInitCallback: (learnProject: Project) -> Unit) {
    val copied = copyLearningProjectFiles(contentRoot, langSupport)
    if (!copied) return
    createVersionFile(contentRoot)
    openOrImportLearningProject(contentRoot, openProjectTask, langSupport) {
      updateLearningModificationTimestamp(it)
      postInitCallback(it)
    }
  }

  private fun updateLearningModificationTimestamp(it: Project) {
    PropertiesComponent.getInstance(it).setValue(LEARNING_PROJECT_MODIFICATION, System.currentTimeMillis().toString())
  }

  private fun openOrImportLearningProject(contentRoot: Path,
                                          openProjectTask: OpenProjectTask,
                                          langSupport: LangSupport,
                                          postInitCallback: (learnProject: Project) -> Unit) {
    val projectDirectoryVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(
      langSupport.getLearningProjectPath(contentRoot)
    ) ?: error("Copied Learn project folder is null")

    val task = openProjectTask.copy(beforeInit = {
      NOTIFICATIONS_SILENT_MODE.set(it, true)
    })
    invokeLater {
      val nioPath = projectDirectoryVirtualFile.toNioPath()
      val project = ProjectUtil.openOrImport(nioPath, task)
                    ?: error("Could not create project for ${langSupport.primaryLanguage} at $nioPath")
      project.setTrusted(true)
      postInitCallback(project)
    }
  }

  private fun copyLearningProjectFiles(newContentDirectory: Path, langSupport: LangSupport): Boolean {
    var targetDirectory = newContentDirectory
    if (!langSupport.copyLearningProjectFiles(targetDirectory.toFile())) {
      targetDirectory = invokeAndWaitIfNeeded {
        chooseParentDirectoryForLearningProject(langSupport)
      } ?: return false
      if (!langSupport.copyLearningProjectFiles(targetDirectory.toFile())) {
        invokeLater {
          Messages.showInfoMessage(LearnBundle.message("learn.project.initializing.cannot.create.message"),
                                   LearnBundle.message("learn.project.initializing.cannot.create.title"))
        }
        error("Cannot create learning demo project. See LOG files for details.")
      }
    }
    val path = langSupport.getLearningProjectPath(targetDirectory).toAbsolutePath().toString()
    LangManager.getInstance().setLearningProjectPath(langSupport, path)
    return true
  }

  fun learningProjectUrl(langSupport: LangSupport) =
    langSupport.javaClass.classLoader.getResource(langSupport.projectResourcePath)
    ?: throw IllegalArgumentException("No project ${langSupport.projectResourcePath} in resources " +
                                      "for ${langSupport.primaryLanguage} IDE learning course")

  private fun chooseParentDirectoryForLearningProject(langSupport: LangSupport): Path? {
    val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
      .withTitle(LearnBundle.message("learn.project.initializing.choose.place", langSupport.contentRootDirectoryName))
    val dialog = FileChooserDialogImpl(descriptor, null)
    var result: List<VirtualFile>? = null
    dialog.choose(VfsUtil.getUserHomeDir(), Consumer { result = it })
    val directories = result ?: return null
    if (directories.isEmpty())
      error("No directory selected for the project")
    val chosen = directories.single()
    val canonicalPath = chosen.canonicalPath ?: error("No canonical path for $chosen")
    return File(canonicalPath, langSupport.contentRootDirectoryName).toPath()
  }

  private fun copyLearnProjectIcon(projectDir: File) {
    val iconPath = "learnProjects/.idea"
    val iconUrl = ProjectUtils::class.java.classLoader.getResource(iconPath) ?: throw IllegalArgumentException(
      "Unable to locate icon for learn project by path: $iconPath")
    val ideaDir = File(projectDir, ".idea")
    FileUtil.ensureExists(ideaDir)
    FileUtils.copyResourcesRecursively(iconUrl, ideaDir)
  }

  private fun createVersionFile(newProjectDirectory: Path) {
    PrintWriter(newProjectDirectory.resolve(FEATURE_TRAINER_VERSION).toFile(), "UTF-8").use {
      it.println(featureTrainerVersion)
    }
  }

  private fun isSameVersion(versionFile: Path): Boolean {
    if (!versionFile.exists()) return false
    val res = Files.lines(versionFile).findFirst()
    if (res.isPresent) {
      return featureTrainerVersion == res.get()
    }
    return false
  }

  private fun versionFile(dest: Path) = dest.resolve(FEATURE_TRAINER_VERSION)

  fun createSdkDownloadingNotification(): Notification {
    val notificationGroup = NotificationGroup.findRegisteredGroup("IDE Features Trainer")
                            ?: error("Not found notificationGroup for IDE Features Trainer")
    return notificationGroup.createNotification(LearnBundle.message("learn.project.initializing.jdk.download.notification.title"),
                                                LearnBundle.message("learn.project.initializing.jdk.download.notification.message",
                                                                    ApplicationNamesInfo.getInstance().fullProductName),
                                                NotificationType.INFORMATION)
  }

  fun closeAllEditorsInProject(project: Project) {
    FileEditorManagerEx.getInstanceEx(project).windows.forEach {
      it.files.forEach { file -> it.closeFile(file) }
    }
  }

  fun restoreProject(languageSupport: LangSupport, project: Project) {
    val stamp = PropertiesComponent.getInstance(project).getValue(LEARNING_PROJECT_MODIFICATION)?.toLong() ?: 0
    val needReplace = mutableListOf<Path>()
    val validContent = mutableListOf<Path>()
    val directories = mutableListOf<Path>()
    val root = getProjectRoot(project)
    val contentRootPath = languageSupport.getContentRootPath(root.toNioPath())

    invokeAndWaitIfNeeded {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    for (path in Files.walk(contentRootPath)) {
      if (contentRootPath.relativize(path).any { file ->
          file.name == ".idea" ||
          file.name == "git" ||
          file.name == ".git" ||
          file.name == ".gitignore" ||
          file.name == "venv" ||
          file.name == FEATURE_TRAINER_VERSION ||
          file.name.endsWith(".iml")
        }) continue
      if (path.isDirectory()) {
        directories.add(path)
      }
      else {
        if (path.getLastModifiedTime().toMillis() > stamp) {
          needReplace.add(path)
        }
        else {
          validContent.add(path)
        }
      }
    }

    var modified = false

    for (path in needReplace) {
      path.delete()
      modified = true
    }

    val contentRoodDirectory = contentRootPath.toFile()
    languageSupport.copyLearningProjectFiles(contentRoodDirectory, FileFilter {
      val path = it.toPath()
      val needCopy = needReplace.contains(path) || !validContent.contains(path)
      modified = needCopy || modified
      needCopy
    })

    for (path in directories) {
      if (isEmptyDir(path)) {
        modified = true
        path.delete()
      }
    }

    if (modified) {
      VfsUtil.markDirtyAndRefresh(false, true, true, root)
      PropertiesComponent.getInstance(project).setValue(LEARNING_PROJECT_MODIFICATION, System.currentTimeMillis().toString())
    }
  }

  private fun isEmptyDir(path: Path): Boolean {
    if (Files.isDirectory(path)) {
      Files.newDirectoryStream(path).use { directory -> return !directory.iterator().hasNext() }
    }
    return false
  }
}