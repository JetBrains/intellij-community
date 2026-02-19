// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logGitChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logGitFinished
import com.intellij.ide.wizard.GitNewProjectWizardStep.CoroutineScopeService.Companion.coroutineScope
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GIT_PROPERTY_NAME
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.refreshAndFindVirtualDirectory
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/**
 * Responsible for handling Git repository initialization if the user enables it.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html#common-steps">
 *   New Project Wizard API: Common Steps (IntelliJ Platform Docs)</a>
 */
class GitNewProjectWizardStep(
  parent: NewProjectWizardBaseStep
) : AbstractNewProjectWizardStep(parent),
    NewProjectWizardBaseData by parent,
    GitNewProjectWizardData {

  private val gitRepositoryInitializer = GitRepositoryInitializer.getInstance()

  private val isGitStepEnabled = gitRepositoryInitializer != null && context.isCreatingNewProject

  private val gitProperty = propertyGraph.property(false)
    .bindBooleanStorage(GIT_PROPERTY_NAME)

  override val git: Boolean get() = isGitStepEnabled && gitProperty.get()

  override fun setupUI(builder: Panel) {
    if (isGitStepEnabled) {
      with(builder) {
        row("") {
          checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"))
            .bindSelected(gitProperty)
            .whenStateChangedFromUi { logGitChanged(it) }
            .onApply { logGitFinished(git) }
        }.bottomGap(BottomGap.SMALL)
      }
    }
  }

  override fun setupProject(project: Project) {
    if (git) {
      runAfterOpened(project) { project ->
        project.trackActivityBlocking(NewProjectWizardActivityKey) {
          project.coroutineScope.launchTracked {
            setupProjectSafe(project, UIBundle.message("error.project.wizard.new.project.git")) {
              initRepository(project)
            }
          }
        }
      }
    }
  }

  private suspend fun initRepository(project: Project) {
    withBackgroundProgress(project, IdeBundle.message("progress.title.creating.git.repository")) {
      Path.of(contentEntryPath).refreshAndFindVirtualDirectory()?.let { rootDirectory ->
        gitRepositoryInitializer!!.initRepository(project, rootDirectory, true)
      }
    }
  }

  init {
    data.putUserData(GitNewProjectWizardData.KEY, this)
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      val Project.coroutineScope: CoroutineScope
        get() = service<CoroutineScopeService>().coroutineScope
    }
  }
}