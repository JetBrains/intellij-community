// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.EMPTY_LABEL
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import java.nio.file.Path

class GitNewProjectWizardStep(
  parent: NewProjectWizardBaseStep
) : AbstractNewProjectWizardStep(parent),
    NewProjectWizardBaseData by parent,
    GitNewProjectWizardData {

  override val gitProperty = propertyGraph.property(false)
    .bindBooleanStorage("NewProjectWizard.gitState")

  override var git by gitProperty

  override fun setupUI(builder: Panel) {
    with(builder) {
      row(EMPTY_LABEL) {
        checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"))
          .bindSelected(gitProperty)
      }.bottomGap(BottomGap.SMALL)
    }
  }

  override fun setupProject(project: Project) {
    if (git) {
      val projectBaseDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path, name))
      if (projectBaseDirectory != null) {
        runBackgroundableTask(IdeBundle.message("progress.title.creating.git.repository"), project) {
          GitRepositoryInitializer.getInstance()!!.initRepository(project, projectBaseDirectory)
        }
      }
    }
    NewProjectWizardCollector.logGitFinished(context, git)
  }

  init {
    data.putUserData(GitNewProjectWizardData.KEY, this)
    gitProperty.afterChange {
      NewProjectWizardCollector.logGitChanged(context)
    }
  }
}