// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.project

import com.intellij.CommonBundle
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
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
import com.intellij.openapi.vfs.*
import com.intellij.util.Consumer
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.LearnBundle
import training.util.featureTrainerVersion
import java.io.File
import java.io.FileFilter
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ProjectUtils {
  private const val LEARNING_PROJECT_MODIFICATION = "LEARNING_PROJECT_MODIFICATION"
  private const val FEATURE_TRAINER_VERSION = "feature-trainer-version.txt"

  private val ideProjectsBasePath by lazy {
    val ideaProjectsPath = WizardContext(null, null).projectFileDirectory
    val ideaProjects = File(ideaProjectsPath)
    FileUtils.ensureDirectoryExists(ideaProjects)
    return@lazy ideaProjectsPath
  }

  /**
   * For example:
   * @projectPath = "/learnProjects/SimpleProject"
   * @projectName = "SimpleProject"
   *
   */
  fun importOrOpenProject(langSupport: LangSupport, projectToClose: Project?, postInitCallback: (learnProject: Project) -> Unit) {
    runBackgroundableTask(LearnBundle.message("learn.project.initializing.process"), project = projectToClose) {
      val path = LangManager.getInstance().getLearningProjectPath(langSupport)
      val defaultDirectoryName = langSupport.defaultProjectName
      val canonicalPlace = File(ideProjectsBasePath, defaultDirectoryName).toPath()
      var dest = if (path == null || !Paths.get(path).isDirectory()) canonicalPlace else Paths.get(path)

      val sameVersion = if (dest.isDirectory()) {
        val versionFile = versionFile(dest)
        if (!versionFile.exists()) {
          val dialogResult = invokeAndWaitIfNeeded {

            val changeDirectory = Messages.showYesNoCancelDialog(
              LearnBundle.message("learn.project.initializing.no.version.file.message", dest, defaultDirectoryName),
              LearnBundle.message("learn.project.initializing.no.version.file.title", defaultDirectoryName),
              LearnBundle.message("learn.project.initializing.no.version.file.choose.another.location"),
              LearnBundle.message("learn.project.initializing.no.version.file.rewrite.project", defaultDirectoryName),
              CommonBundle.getCancelButtonText(), null)
            if (changeDirectory == Messages.YES) {
              dest = chooseParentDirectoryForLearningProject(langSupport) ?: return@invokeAndWaitIfNeeded Messages.CANCEL
            }
            changeDirectory
          }
          if (dialogResult == Messages.CANCEL) return@runBackgroundableTask
          false
        }
        else isSameVersion(versionFile)
      }
      else false
      if (!sameVersion) {
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
        LangManager.getInstance().setLearningProjectPath(langSupport, dest.toAbsolutePath().toString())
        openOrImportLearningProject(dest, OpenProjectTask(projectToClose = projectToClose), langSupport, postInitCallback)
      }
    }
  }

  fun simpleInstallAndOpenLearningProject(projectPath: Path,
                                          langSupport: LangSupport,
                                          openProjectTask: OpenProjectTask,
                                          postInitCallback: (learnProject: Project) -> Unit) {
    val copied = copyLearningProjectFiles(projectPath, langSupport)
    if (!copied) return
    createVersionFile(projectPath)
    openOrImportLearningProject(projectPath, openProjectTask, langSupport, postInitCallback)
  }

  private fun openOrImportLearningProject(projectPath: Path,
                                          openProjectTask: OpenProjectTask,
                                          langSupport: LangSupport,
                                          postInitCallback: (learnProject: Project) -> Unit) {
    val projectDirectoryVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectPath)
                                      ?: error("Copied Learn project folder is null")
    val task = openProjectTask.copy(beforeInit = {
      NOTIFICATIONS_SILENT_MODE.set(it, true)
    })
    invokeLater {
      val project = ProjectUtil.openOrImport(projectDirectoryVirtualFile.toNioPath(), task)
                    ?: error("Could not create project for ${langSupport.primaryLanguage}")
      PropertiesComponent.getInstance(project).setValue(LEARNING_PROJECT_MODIFICATION, System.currentTimeMillis().toString())
      postInitCallback(project)
    }
  }

  private fun copyLearningProjectFiles(newProjectDirectory: Path, langSupport: LangSupport): Boolean {
    var targetDirectory = newProjectDirectory
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
    LangManager.getInstance().setLearningProjectPath(langSupport, targetDirectory.toAbsolutePath().toString())
    return true
  }

  fun learningProjectUrl(langSupport: LangSupport) =
    langSupport.javaClass.classLoader.getResource(langSupport.projectResourcePath)
    ?: throw IllegalArgumentException("No project ${langSupport.projectResourcePath} in resources for ${langSupport.primaryLanguage} IDE learning course")

  private fun chooseParentDirectoryForLearningProject(langSupport: LangSupport): Path? {
    val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
      .withTitle(LearnBundle.message("learn.project.initializing.choose.place", langSupport.defaultProjectName))
    val dialog = FileChooserDialogImpl(descriptor, null)
    var result: List<VirtualFile>? = null
    dialog.choose(VfsUtil.getUserHomeDir(), Consumer { result = it })
    val directories = result ?: return null
    if (directories.isEmpty())
      error("No directory selected for the project")
    val chosen = directories.single()
    val canonicalPath = chosen.canonicalPath ?: error("No canonical path for $chosen")
    return File(canonicalPath, langSupport.defaultProjectName).toPath()
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
                                                                    ApplicationNamesInfo.getInstance().fullProductName))
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
    val root = ProjectRootManager.getInstance(project).contentRoots[0]
    invokeAndWaitIfNeeded {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    runReadAction {
      VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Void>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if(file.name == ".idea" ||
             file.name == "venv" ||
             file.name == FEATURE_TRAINER_VERSION ||
             file.name.endsWith(".iml")) return false

          if (file.isDirectory) return true

          val path = file.toNioPath()
          if (file.timeStamp > stamp) {
            needReplace.add(path)
          }
          else {
            validContent.add(path)
          }
          return true
        }
      })
    }

    var modified = false

    for (path in needReplace) {
      path.delete()
      modified = true
    }

    val pathname = project.basePath ?: throw IllegalStateException("No Base Path in Learning project")
    languageSupport.copyLearningProjectFiles(File(pathname), FileFilter {
      val path = it.toPath()
      val needCopy = needReplace.contains(path) || !validContent.contains(path)
      modified = needCopy || modified
      needCopy
    })

    if (modified) {
      VfsUtil.markDirtyAndRefresh(false, true, true, root)
      PropertiesComponent.getInstance(project).setValue(LEARNING_PROJECT_MODIFICATION, System.currentTimeMillis().toString())
    }
  }
}