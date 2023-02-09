// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logGitChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logGitFinished
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GIT_PROPERTY_NAME
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.refreshAndFindVirtualDirectory
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi

class GitNewProjectWizardStep(
  parent: NewProjectWizardBaseStep
) : AbstractNewProjectWizardStep(parent),
    NewProjectWizardBaseData by parent,
    GitNewProjectWizardData {

  private val gitRepositoryInitializer = GitRepositoryInitializer.getInstance()

  private val gitProperty = propertyGraph.property(false)
    .bindBooleanStorage(GIT_PROPERTY_NAME)

  override val git get() = gitRepositoryInitializer != null && gitProperty.get()

  override fun setupUI(builder: Panel) {
    if (gitRepositoryInitializer != null) {
      with(builder) {
        row("") {
          checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"))
            .bindSelected(gitProperty)
            .whenStateChangedFromUi { logGitChanged() }
        }.bottomGap(BottomGap.SMALL)
      }
    }
  }

  override fun setupProject(project: Project) {
    setupProjectSafe(project, UIBundle.message("error.project.wizard.new.project.git")) {
      if (git) {
        val rootDirectory = path.toNioPath().resolve(name).refreshAndFindVirtualDirectory()
        if (rootDirectory != null) {
          whenProjectCreated(project) {
            runBackgroundableTask(IdeBundle.message("progress.title.creating.git.repository"), project) {
              setupProjectSafe(project, UIBundle.message("error.project.wizard.new.project.git")) {
                gitRepositoryInitializer!!.initRepository(project, rootDirectory, true)
              }
            }
          }
        }
      }
      logGitFinished(git)
    }
  }

  init {
    data.putUserData(GitNewProjectWizardData.KEY, this)
  }
}