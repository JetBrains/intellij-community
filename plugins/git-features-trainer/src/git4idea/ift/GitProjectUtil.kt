// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.actions.GitInit
import training.project.FileUtils
import training.project.ProjectUtils
import java.io.File

object GitProjectUtil {
  fun restoreGitLessonsFiles(project: Project) {
    val learningProjectPath = ProjectUtils.getProjectRoot(project).toNioPath()
    val learningProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(learningProjectPath)
                              ?: error("Learning project not found")
    val gitProjectRoot = invokeAndWaitIfNeeded {
      runWriteAction {
        learningProjectRoot.findChild("git")?.apply {
          findChild(".git")?.delete(this)
        } ?: learningProjectRoot.createChildDirectory(this, "git")
      }
    }

    copyGitProject(gitProjectRoot.toNioPath().toFile()).also {
      if (it) {
        GitInit.refreshAndConfigureVcsMappings(project, gitProjectRoot, gitProjectRoot.path)
      }
      else error("Failed to copy git project")
    }
  }

  fun copyGitProject(destination: File): Boolean {
    // Classloader of Git IFT plugin is required here
    val gitProjectURL = this.javaClass.classLoader.getResource("learnProjects/GitProject") ?: error("GitProject not found")
    return FileUtils.copyResourcesRecursively(gitProjectURL, destination)
  }
}