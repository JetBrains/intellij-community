// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.buildActionRunner

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.jetbrains.plugins.gradle.importing.BuildFinishedModel
import org.jetbrains.plugins.gradle.importing.BuildFinishedModelProvider
import org.jetbrains.plugins.gradle.importing.ProjectLoadedModel
import org.jetbrains.plugins.gradle.importing.ProjectLoadedModelProvider
import org.jetbrains.plugins.gradle.tooling.builder.ProjectPropertiesTestModelBuilder
import org.junit.Test

class GradlePhasedSyncTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test one-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val buildCompletionAssertion = ListenerAssertion()

      addToolingExtensionClasses(disposable, ProjectPropertiesTestModelBuilder.ProjectProperties::class.java)
      addProjectModelProviders(disposable, ProjectLoadedModelProvider(), BuildFinishedModelProvider())
      whenBuildCompleted(disposable) { resolverContext ->
        buildCompletionAssertion.trace {
          val buildModel = resolverContext.allBuilds.single()
          val projectModel = buildModel.projects.single()
          val projectLoadedModel = resolverContext.getProjectModel(projectModel, ProjectLoadedModel::class.java)
          val buildFinishedModel = resolverContext.getProjectModel(projectModel, BuildFinishedModel::class.java)
          assertNotNull("Expected ProjectLoadedModel on the build completion", projectLoadedModel)
          assertNotNull("Expected BuildFinishedModel on the build completion", buildFinishedModel)
        }
      }

      createSettingsFile("")
      importProject()

      buildCompletionAssertion.assertListenerState(1) { "Build action should be finished only once" }
    }
  }

  @Test
  fun `test two-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val buildCompletionAssertion = ListenerAssertion()

      addToolingExtensionClasses(disposable, ProjectPropertiesTestModelBuilder.ProjectProperties::class.java)
      addProjectModelProviders(disposable, ProjectLoadedModelProvider(), BuildFinishedModelProvider())
      whenProjectLoaded(disposable) { resolverContext ->
        projectLoadingAssertion.trace {
          val buildModel = resolverContext.allBuilds.single()
          val projectModel = buildModel.projects.single()
          val projectLoadedModel = resolverContext.getProjectModel(projectModel, ProjectLoadedModel::class.java)
          assertNotNull("Expected ProjectLoadedModel on the project loaded phase", projectLoadedModel)
        }
      }
      whenBuildCompleted(disposable) { resolverContext ->
        buildCompletionAssertion.trace {
          val buildModel = resolverContext.allBuilds.single()
          val projectModel = buildModel.projects.single()
          val projectLoadedModel = resolverContext.getProjectModel(projectModel, ProjectLoadedModel::class.java)
          val buildFinishedModel = resolverContext.getProjectModel(projectModel, BuildFinishedModel::class.java)
          assertNotNull("Expected ProjectLoadedModel on the build completion", projectLoadedModel)
          assertNotNull("Expected BuildFinishedModel on the build completion", buildFinishedModel)
        }
      }

      createSettingsFile("")
      importProject()

      projectLoadingAssertion.assertListenerState(1) { "Project loaded phase should be finished only once" }
      buildCompletionAssertion.assertListenerState(1) { "Build action should be finished only once" }
    }
  }
}