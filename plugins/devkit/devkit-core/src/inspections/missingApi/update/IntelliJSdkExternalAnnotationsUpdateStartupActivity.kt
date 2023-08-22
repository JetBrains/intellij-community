// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.missingApi.update

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk

/**
 * Startup activity that updates external annotations of IDEA JDKs configured in the project.
 */
internal class IntelliJSdkExternalAnnotationsUpdateStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project): Unit = blockingContext {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) {
      return@blockingContext
    }

    val ideaJdks = ProjectJdkTable.getInstance().getSdksOfType(IdeaJdk.getInstance())
    for (ideaJdk in ideaJdks) {
      updateAnnotationsLaterIfNecessary(project, ideaJdk)
    }

    subscribeToJdkChanges(project, application)
  }

  private fun updateAnnotationsLaterIfNecessary(project: Project, ideaJdk: Sdk) {
    IntelliJSdkExternalAnnotationsUpdater.getInstance().updateIdeaJdkAnnotationsIfNecessary(project, ideaJdk)
  }

  private fun subscribeToJdkChanges(project: Project, application: Application) {
    val connection = application.messageBus.connect(project)
    connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkAdded(jdk: Sdk) {
        if (jdk.sdkType == IdeaJdk.getInstance()) {
          updateAnnotationsLaterIfNecessary(project, jdk)
        }
      }

      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
        if (jdk.sdkType == IdeaJdk.getInstance()) {
          updateAnnotationsLaterIfNecessary(project, jdk)
        }
      }
    })
  }
}