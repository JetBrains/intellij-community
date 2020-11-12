// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.project

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.LearnBundle
import training.util.featureTrainerVersion
import java.io.File
import java.io.PrintWriter
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

object ProjectUtils {
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
      val path = LangManager.getInstance().state.languageToProjectMap[langSupport.primaryLanguage]
      val canonicalPlace = File(ideProjectsBasePath, langSupport.defaultProjectName).toPath()
      var dest = if (path == null) canonicalPlace else Paths.get(path)
      if (!isSameVersion(dest)) {
        if (dest.exists()) {
          dest.delete()
        }
        else {
          dest = canonicalPlace
        }
        langSupport.installAndOpenLearningProject(dest, projectToClose) {
          it.basePath?.let { path ->
            copyLearnProjectIcon(File(path))
          }
          postInitCallback(it)
        }
      }
      else {
        val projectDirectoryVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dest)
                                          ?: error("Copied Learn project folder is null")
        invokeLater {
          val project = ProjectUtil.openOrImport(projectDirectoryVirtualFile.toNioPath(), OpenProjectTask(projectToClose = projectToClose))
                        ?: error("Could not create project for ${langSupport.primaryLanguage}")
          postInitCallback(project)
        }
      }
    }
  }

  fun copyLearningProjectFiles(newProjectDirectory: Path, langSupport: LangSupport) {
    var targetDirectory = newProjectDirectory
    val inputUrl: URL = langSupport.javaClass.classLoader.getResource(langSupport.projectResourcePath)
                        ?: throw IllegalArgumentException(
                          "No project ${langSupport.projectResourcePath} in resources for ${langSupport.primaryLanguage} IDE learning course")
    if (!FileUtils.copyResourcesRecursively(inputUrl, targetDirectory.toFile())) {
      val directories = invokeAndWaitIfNeeded {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
          .withTitle(LearnBundle.message("learn.project.initializing.choose.place"))
        val dialog = FileChooserDialogImpl(descriptor, null)
        val result = CompletableFuture<List<VirtualFile>>()
        dialog.choose(VfsUtil.getUserHomeDir(), Consumer { result.complete(it) })
        result
      }.get()
      if (directories.isEmpty())
        error("No directory selected for the project")
      val chosen = directories.single()
      val canonicalPath = chosen.canonicalPath ?: error("No canonical path for $chosen")
      targetDirectory = File(canonicalPath, langSupport.defaultProjectName).toPath()
      if (!FileUtils.copyResourcesRecursively(inputUrl, targetDirectory.toFile())) {
        invokeLater {
          Messages.showInfoMessage(LearnBundle.message("learn.project.initializing.cannot.create.message"),
                                   LearnBundle.message("learn.project.initializing.cannot.create.title"))
        }
        error("Cannot create learning demo project. See LOG files for details.")
      }
    }
    LangManager.getInstance().state.languageToProjectMap[langSupport.primaryLanguage] = targetDirectory.toAbsolutePath().toString()
  }

  private fun copyLearnProjectIcon(projectDir: File) {
    val iconPath = "/learnProjects/.idea"
    val iconUrl = ProjectUtils::class.java.classLoader.getResource(iconPath) ?: throw IllegalArgumentException("Unable to locate icon for learn project by path: $iconPath")
    val ideaDir = File(projectDir, ".idea")
    FileUtil.ensureExists(ideaDir)
    FileUtils.copyResourcesRecursively(iconUrl, ideaDir)
  }

  fun createVersionFile(newProjectDirectory: Path) {
    PrintWriter(newProjectDirectory.resolve("feature-trainer-version.txt").toFile(), "UTF-8").use {
      it.println(featureTrainerVersion)
    }
  }

  private fun isSameVersion(dest: Path): Boolean {
    if (!dest.exists()) {
      return false
    }
    val versionFile = versionFile(dest)
    if (!versionFile.exists()) {
      return false
    }
    val res = Files.lines(versionFile).findFirst()
    if (res.isPresent) {
      return featureTrainerVersion == res.get()
    }
    return false
  }

  private fun versionFile(dest: Path) = dest.resolve("feature-trainer-version.txt")

  fun closeAllEditorsInProject(project: Project) {
    FileEditorManagerEx.getInstanceEx(project).windows.forEach {
      it.files.forEach { file -> it.closeFile(file) }
    }
  }
}