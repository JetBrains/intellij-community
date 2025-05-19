// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.missingApi.update

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ProjectJdkTable.Listener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.launch
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk

/**
 * Startup activity that updates external annotations of IDEA JDKs configured in the project.
 */
private class IntelliJSdkExternalAnnotationsUpdateStartupActivity : ProjectActivity {
  init {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val ideaJdks = serviceAsync<ProjectJdkTable>().getSdksOfType(IdeaJdk.getInstance())
    val sdkAnnotationsUpdater = serviceAsync<IntelliJSdkExternalAnnotationsUpdater>()
    for (ideaJdk in ideaJdks) {
      sdkAnnotationsUpdater.updateIdeaJdkAnnotationsIfNecessary(project, ideaJdk)
    }

    val connection = ApplicationManager.getApplication().messageBus.connect(sdkAnnotationsUpdater.coroutineScope)
    connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : Listener {
      override fun jdkAdded(jdk: Sdk) {
        if (jdk.sdkType == IdeaJdk.getInstance()) {
          sdkAnnotationsUpdater.coroutineScope.launch {
            sdkAnnotationsUpdater.updateIdeaJdkAnnotationsIfNecessary(project, jdk)
          }
        }
      }

      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
        if (jdk.sdkType == IdeaJdk.getInstance()) {
          sdkAnnotationsUpdater.coroutineScope.launch {
            sdkAnnotationsUpdater.updateIdeaJdkAnnotationsIfNecessary(project, jdk)
          }
        }
      }
    })
  }
}