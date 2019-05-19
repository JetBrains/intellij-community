// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubSettings
import org.jetbrains.plugins.github.util.GithubUtil

class GithubSettingsConfigurable internal constructor(private val project: Project,
                                                      private val settings: GithubSettings,
                                                      private val accountManager: GithubAccountManager,
                                                      private val defaultAccountHolder: GithubProjectDefaultAccountHolder,
                                                      private val executorFactory: GithubApiRequestExecutor.Factory,
                                                      private val avatarLoader: CachingGithubUserAvatarLoader,
                                                      private val imageResizer: GithubImageResizer) :
  ConfigurableBase<GithubSettingsPanel, GithubSettingsConfigurable.GithubSettingsHolder>("settings.github",
                                                                                         GithubUtil.SERVICE_DISPLAY_NAME,
                                                                                         "settings.github"),
  Configurable.NoMargin {

  init {
    ApplicationManager.getApplication().messageBus
      .connect(project)
      .subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, object : AccountTokenChangedListener {
        override fun tokenChanged(account: GithubAccount) {
          if (!isModified) reset()
        }
      })
  }

  override fun getSettings(): GithubSettingsHolder {
    return GithubSettingsHolder(settings, accountManager, defaultAccountHolder)
  }

  override fun createUi(): GithubSettingsPanel {
    return GithubSettingsPanel(project, executorFactory, avatarLoader, imageResizer)
  }

  inner class GithubSettingsHolder internal constructor(val application: GithubSettings,
                                                        val applicationAccounts: GithubAccountManager,
                                                        val projectAccount: GithubProjectDefaultAccountHolder)
}
