// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectStorePathManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import java.nio.file.Path

/**
 * Open project with `.idea`.
 */
private class ProjectDirCheckoutListener : CheckoutListener {
  override fun processCheckedOutDirectory(project: Project, directory: Path): Boolean {
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    // todo Rider project layout - several.idea.solution-name names
    if (!service<ProjectStorePathManager>().testStoreDirectoryExistsForProjectRoot(directory)) {
      return false
    }
    runBlockingCancellable {
      ProjectManagerEx.getInstanceEx().openProjectAsync(directory, OpenProjectTask {
        projectToClose = project
        forceReuseFrame = willOpenFromWelcomeScreenProject(project)
      })
    }
    return true
  }
}

/**
 * Open directory.
 */
private class PlatformProjectCheckoutListener : CheckoutListener {
  override fun processCheckedOutDirectory(project: Project, directory: Path): Boolean {
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    return runBlockingCancellable {
      ProjectUtil.openOrImportAsync(directory, OpenProjectTask {
        projectToClose = project
        forceReuseFrame = willOpenFromWelcomeScreenProject(project)
      }) != null
    }
  }
}

private fun willOpenFromWelcomeScreenProject(project: Project): Boolean {
  return WelcomeScreenProjectProvider.isWelcomeScreenProject(project)
}
