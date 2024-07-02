// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.collaboration.auth.PersistentDefaultAccountHolder
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUtil

/**
 * Handles default Github account for project
 */
@Service(Service.Level.PROJECT)
@State(name = "GithubDefaultAccount", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GithubProjectDefaultAccountHolder(project: Project, parentCs: CoroutineScope)
  : PersistentDefaultAccountHolder<GithubAccount>(project, parentCs.childScope()) {

  override fun accountManager() = service<GHAccountManager>()

  override fun notifyDefaultAccountMissing() = runInEdt {
    val title = GithubBundle.message("accounts.default.missing")
    GithubUtil.LOG.info("${title}; ${""}")
    VcsNotifier.importantNotification().createNotification(title, NotificationType.WARNING)
      .setDisplayId(GithubNotificationIdsHolder.MISSING_DEFAULT_ACCOUNT)
      .addAction(GithubNotifications.getConfigureAction(project))
      .notify(project)
  }
}