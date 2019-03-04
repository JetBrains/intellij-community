// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.update

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk

/**
 * Startup activity that updates external annotations of IDEA JDKs configured in the project.
 */
class IdeExternalAnnotationsUpdateStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) {
      return
    }

    subscribeToJdkChanges(project, application)
  }

  private fun updateAnnotationsLaterIfNecessary(project: Project, ideaJdk: Sdk) {
    val buildNumber = getIdeaBuildNumber(ideaJdk)
    if (buildNumber != null) {
      IdeExternalAnnotationsUpdater.getInstance().updateIdeaJdkAnnotationsIfNecessary(project, ideaJdk, buildNumber)
    }
  }

  private fun subscribeToJdkChanges(project: Project, application: Application) {
    val connection = application.messageBus.connect(project)
    connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Adapter() {
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

  private fun getIdeaBuildNumber(ideaJdk: Sdk): BuildNumber? {
    val homePath = ideaJdk.homePath ?: return null
    val buildNumberStr = IdeaJdk.getBuildNumber(homePath) ?: return null
    return BuildNumber.fromStringOrNull(buildNumberStr)
  }
}