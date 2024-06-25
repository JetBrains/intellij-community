// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.dvcs.push.PushSpec
import com.intellij.dvcs.push.Pusher
import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.update.GitUpdateInfoAsLog
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.CancellationException

private val LOG = logger<GitPusher>()

class GitPusher(
  private val project: Project,
  private val settings: GitVcsSettings,
  private val pushSupport: GitPushSupport
) : Pusher<GitRepository, GitPushSource, GitPushTarget>() {

  override fun push(
    pushSpecs: Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>>,
    additionalOption: VcsPushOptionValue?,
    force: Boolean
  ) {
    push(pushSpecs, additionalOption, force, emptyMap())
  }

  override fun push(
    pushSpecs: Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>>,
    optionValue: VcsPushOptionValue?,
    force: Boolean,
    customParams: Map<String, VcsPushOptionValue>
  ) {
    expireExistingErrorsAndWarnings()

    val (pushTagMode: GitPushTagMode?, skipHook: Boolean) = when (optionValue) {
      is GitVcsPushOptionValue -> optionValue.pushTagMode to optionValue.isSkipHook
      else -> null to false
    }

    settings.pushTagMode = pushTagMode

    val pushOperation = GitPushOperation(project, pushSupport, pushSpecs, pushTagMode, force, skipHook)
    pushAndNotify(project, pushOperation, customParams)
  }

  private fun expireExistingErrorsAndWarnings() {
    val existingNotifications: Array<GitPushResultNotification> = NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(GitPushResultNotification::class.java, project)
    for (notification in existingNotifications) {
      if (notification.type != NotificationType.INFORMATION) {
        notification.expire()
      }
    }
  }

  companion object {
    @JvmStatic
    fun pushAndNotify(
      project: Project,
      pushOperation: GitPushOperation,
      customParams: Map<String, VcsPushOptionValue>
    ) {
      val pushResult = pushOperation.execute()
      val pushListener = project.messageBus.syncPublisher(GitPushListener.TOPIC)
      pushResult.results.forEach { (gitRepository, pushRepoResult) ->
        pushListener.onCompleted(gitRepository, pushRepoResult, customParams)
      }

      val updatedRanges = pushResult.updatedRanges
      val notificationData = if (!updatedRanges.isEmpty()) {
        GitUpdateInfoAsLog(project, updatedRanges).calculateDataAndCreateLogTab()
      }
      else {
        null
      }

      val actions = runBlockingCancellable {
        GitPushNotificationCustomizer.EP_NAME.getExtensions(project).flatMap { extension ->
          pushResult.results.flatMap { (gitRepository, pushRepoResult) ->
            try {
              extension.getActions(gitRepository, pushRepoResult, customParams)
            }
            catch (e: Exception) {
              if (e is CancellationException) currentCoroutineContext().ensureActive()
              else LOG.warn("Error occurred when collecting push notification actions from ${extension::javaClass.name}", e)
              emptyList()
            }
          }
        }
      }

      ApplicationManager.getApplication().invokeLater {
        val multiRepoProject = GitUtil.getRepositoryManager(project).moreThanOneRoot()
        val notification = GitPushResultNotification.create(
          project, pushResult, pushOperation, multiRepoProject, notificationData, customParams
        )

        actions.forEach { action -> notification.addAction(action) }
        notification.notify(project)
      }
    }
  }
}
