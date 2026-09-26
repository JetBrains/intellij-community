// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import git4idea.actions.GitToggleAnnotationOptionsActionProvider
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.CoroutineScope

object GitAnnotationsListener {
  @JvmStatic
  fun registerListener(project: Project, activeScope: CoroutineScope) {
    val busConnection = project.getMessageBus().connect(activeScope)
    busConnection.subscribe(GitRepository.GIT_REPO_CHANGE, RepoListener(project))

    val logSettingsListener = LogSettingsListener(project)
    val logSettings = ApplicationManager.getApplication().service<VcsLogApplicationSettings>()
    logSettings.addChangeListener(logSettingsListener)
    activeScope.awaitCancellationAndInvoke { logSettings.removeChangeListener(logSettingsListener) }
  }

  private class RepoListener(val project: Project) : GitRepositoryChangeListener {
    override fun repositoryChanged(repository: GitRepository) {
      val refresher = BackgroundTaskUtil.syncPublisher<VcsAnnotationRefresher>(project, VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED)
      refresher.dirtyUnder(repository.getRoot())
    }
  }

  private class LogSettingsListener(val project: Project) : PropertiesChangeListener {
    override fun <T> onPropertyChanged(property: VcsLogUiProperty<T>) {
      if (property.equals(CommonUiProperties.PREFER_COMMIT_DATE)) {
        GitToggleAnnotationOptionsActionProvider.resetAllAnnotations(project, false)
      }
    }
  }
}
