// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.lang

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import training.learn.exceptons.NoSdkException
import training.project.FileUtils
import training.project.ProjectUtils
import training.project.ReadMeCreator
import training.util.OnboardingFeedbackData
import java.io.File
import java.io.FileFilter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path

abstract class AbstractLangSupport : LangSupport {
  override val contentRootDirectoryName: String
    get() = "LearnProject"

  override fun getProjectFilePath(projectName: String): String {
    return ProjectUtil.getBaseDir() + File.separator + projectName
  }

  override var onboardingFeedbackData: OnboardingFeedbackData? = null

  override fun installAndOpenLearningProject(contentRoot: Path,
                                             projectToClose: Project?,
                                             postInitCallback: (learnProject: Project) -> Unit) {
    ProjectUtils.simpleInstallAndOpenLearningProject(contentRoot, this,
                                                     OpenProjectTask(projectToClose = projectToClose),
                                                     postInitCallback)
  }

  override fun openOrImportLearningProject(projectRootDirectory: VirtualFile, openProjectTask: OpenProjectTask): Project {
    val nioPath = projectRootDirectory.toNioPath()
    return ProjectUtil.openOrImport(nioPath, openProjectTask) ?: error("Cannot create project for ${primaryLanguage} at $nioPath")
  }

  override fun copyLearningProjectFiles(projectDirectory: File, destinationFilter: FileFilter?): Boolean {
    val inputUrl = ProjectUtils.learningProjectUrl(this)
    return FileUtils.copyResourcesRecursively(inputUrl, projectDirectory, destinationFilter).also {
      if (it) copyGeneratedFiles(projectDirectory, destinationFilter)
    }
  }

  private fun copyGeneratedFiles(projectDirectory: File, destinationFilter: FileFilter?) {
    val generator = readMeCreator
    if (generator != null) {
      val readme = File(projectDirectory, "README.md")
      if (destinationFilter == null || destinationFilter.accept(readme)) {
        PrintWriter(readme, StandardCharsets.UTF_8).use {
          it.print(generator.createReadmeMdText())
        }
      }
    }
  }

  open val readMeCreator: ReadMeCreator? = null

  override fun getSdkForProject(project: Project, selectedSdk: Sdk?): Sdk? {
    try {
      // Use no SDK if it's a valid for this language
      checkSdk(null, project)
      return null
    }
    catch (e: Throwable) {
    }

    return ApplicationManager.getApplication().runReadAction(ThrowableComputable<Sdk, NoSdkException> {
      val sdkOrNull = ProjectJdkTable.getInstance().allJdks.find {
        try {
          checkSdk(it, project)
          true
        }
        catch (e: Throwable) {
          false
        }
      }
      sdkOrNull ?: throw NoSdkException()
    })
  }

  override fun applyProjectSdk(sdk: Sdk, project: Project) {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {

        val rootManager = ProjectRootManagerEx.getInstanceEx(project)
        rootManager.projectSdk = sdk
      }
    }, null, null)
  }

  override fun cleanupBeforeLessons(project: Project) {
    ProjectUtils.restoreProject(this, project)
  }

  override fun toString(): String = primaryLanguage
}