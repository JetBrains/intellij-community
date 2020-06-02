// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.ProjectTopics
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class WorkspaceModelTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule(forceEnableWorkspaceModel = true)

  @Test
  fun `do not fire rootsChanged if there were no changes`() {
    val disposable = Disposer.newDisposable()
    projectModel.project.messageBus.connect(disposable).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        Assert.fail("rootsChanged must not be called if there are no changes")
      }
    })
    disposable.use {
      runWriteActionAndWait {
        WorkspaceModel.getInstance(projectModel.project).updateProjectModel {  }
      }
    }
  }
}