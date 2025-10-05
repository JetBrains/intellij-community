// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GitLabLoginCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("gitlab.login", 1)

  private val IS_GL_COM = EventFields.Boolean("is_gitlab_com")
  private val LOGIN_SOURCE = EventFields.Enum<GitLabLoginSource>("source")
  private val IS_RE_LOGIN = EventFields.Boolean("re_login")
  private val LOGIN_EVENT = GROUP.registerEvent("login", LOGIN_SOURCE, IS_RE_LOGIN, IS_GL_COM)

  override fun getGroup(): EventLogGroup = GROUP

  fun login(loginData: GitLabLoginData) {
    LOGIN_EVENT.log(loginData.source, loginData.isReLogin, loginData.isGitLabDotCom)
  }
}

data class GitLabLoginData(
  val source: GitLabLoginSource,
  val isReLogin: Boolean = false,
  val isGitLabDotCom: Boolean = true,
)

enum class GitLabLoginSource {
  GIT,
  SETTINGS,
  CLONE,
  SHARE,
  MR_TW,
  MR_LIST,
  MR_DETAILS,
  MR_TIMELINE,
  SNIPPET,
  WELCOME_SCREEN,
  UNKNOWN
}