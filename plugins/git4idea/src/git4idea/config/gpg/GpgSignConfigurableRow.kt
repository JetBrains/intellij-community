// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config.gpg

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.Alarm
import com.intellij.util.application
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.config.GitExecutableListener
import git4idea.config.GitExecutableManager
import git4idea.config.HasGitRootsPredicate
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitConfigListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import javax.swing.JLabel

class GpgSignConfigurableRow(val project: Project, val disposable: Disposable) {
  companion object {
    fun Panel.createGpgSignRow(project: Project, disposable: Disposable) {
      val panel = GpgSignConfigurableRow(project, disposable)
      with(panel) {
        createRow()
      }
    }
  }

  private val statusLabel: JLabel = JBLabel().apply {
    foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
  }
  private val errorLabel: JLabel = JBLabel().apply {
    foreground = UIUtil.getErrorForeground()
  }

  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
  private val uiDispatcher get() = AppUIExecutor.onUiThread(ModalityState.any()).coroutineDispatchingContext()
  private val scope = CoroutineScope(SupervisorJob()).also { Disposer.register(disposable) { it.cancel() } }

  private val secretKeys = SecretKeysValue(project)
  private val repoConfigs = mutableMapOf<GitRepository, RepoConfigValue>()

  init {
    secretKeys.addListener { updatePresentation() }
    updatePresentation()

    val connection = project.messageBus.connect(disposable)
    connection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener { scheduleUpdate() })
    connection.subscribe(GitConfigListener.TOPIC, object : GitConfigListener {
      override fun notifyConfigChanged(repository: GitRepository) {
        scheduleUpdate()
      }
    })

    application.messageBus.connect(disposable).subscribe(GitExecutableManager.TOPIC, GitExecutableListener { scheduleUpdate() })

    updateRepoList()
    reloadConfigs()
    reloadSecretKeys()
  }

  private fun Panel.createRow() {
    row {
      button(message("settings.sign.gpg.configure.link.text")) {
        openGpgConfigureDialog()
      }
        .enabledIf(HasGitRootsPredicate(project, disposable))
      cell(statusLabel)
    }
    indent {
      row {
        cell(errorLabel)
      }
    }
  }

  private fun updatePresentation() {
    statusLabel.text = getStatusLabelText()

    val error = repoConfigs.values.mapNotNull { it.error }.firstOrNull()?.message
    errorLabel.isVisible = error != null
    errorLabel.text = error
  }

  private fun scheduleUpdate() {
    if (alarm.isDisposed) return
    alarm.cancelAllRequests()
    alarm.addRequest(Runnable {
      updateRepoList()
      reloadConfigs()
      reloadSecretKeys()
    }, 500, ModalityState.any())
  }

  private fun updateRepoList() {
    val repositories = GitRepositoryManager.getInstance(project).repositories.toSet()

    val toRemove = repoConfigs.keys - repositories
    for (repo in toRemove) {
      repoConfigs.remove(repo)
    }

    for (repository in repositories) {
      repoConfigs.computeIfAbsent(repository) {
        RepoConfigValue(repository).also {
          it.addListener { updatePresentation() }
        }
      }
    }
  }

  private fun reloadConfigs() {
    scope.launch(uiDispatcher + CoroutineName("GpgSignRowPanel - reload configs")) {
      val repoConfigs = repoConfigs.values
      for (config in repoConfigs) {
        config.tryReload()
      }
    }
  }

  private fun reloadSecretKeys() {
    scope.launch(uiDispatcher + CoroutineName("GpgSignRowPanel - reload secret keys")) {
      secretKeys.tryReload()
    }
  }

  private fun openGpgConfigureDialog() {
    val repositories = GitRepositoryManager.getInstance(project).repositories
    if (repositories.isEmpty()) return
    if (repositories.size == 1) {
      val repository = repositories.single()
      val repoConfig = repoConfigs[repository] ?: RepoConfigValue(repository)
      GitGpgConfigDialog(repository, secretKeys, repoConfig).show()
    }
    else {
      GitGpgMultiRootConfigDialog(project, secretKeys, repoConfigs).show()
    }
  }

  private fun getStatusLabelText(): @Nls String {
    val keys = repoConfigs.values.map { it.value }
    if (keys.isEmpty()) return message("settings.label.sign.gpg.commits.no.roots.text")

    val loadedKeys = keys.filterNotNull()
    if (loadedKeys.size != keys.size) return ""

    val totalRepos = loadedKeys.count()
    val configuredRepos = loadedKeys.filter { it.key != null }
    val configuredKeys = configuredRepos.map { it.key!! }.toSet()
    val hasNotConfigured = loadedKeys.any { it.key == null }

    when {
      configuredKeys.isEmpty() -> {
        return message("settings.label.sign.gpg.commits.not.configured.text")
      }
      hasNotConfigured -> {
        return message("settings.label.sign.gpg.commits.enabled.n.roots.of.m.text", configuredRepos.size, totalRepos)
      }
      configuredKeys.size > 1 -> {
        return message("settings.label.sign.gpg.commits.enabled.different.keys.text")
      }
      else -> {
        val key = configuredKeys.single()
        val description = secretKeys.value?.descriptions?.get(key)

        val keyPresentation = when {
          description != null -> "$description (${key.id})"
          else -> key.id
        }
        return message("settings.label.sign.gpg.commits.enabled.text", keyPresentation)
      }
    }
  }
}
