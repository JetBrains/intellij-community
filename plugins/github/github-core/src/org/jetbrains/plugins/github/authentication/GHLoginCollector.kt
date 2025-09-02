// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubServerPath

@ApiStatus.Internal
object GHLoginCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("github.login", 1)

  private val SOURCE = EventFields.Enum<GHLoginSource>("source")
  private val RE_LOGIN = EventFields.Boolean("re_login")
  private val AUTH_TYPE = EventFields.Enum<GHAuthType>("auth_type")
  private val SERVER_OFFERING = EventFields.Enum<GHServerOffering>("server_offering")

  private val LOGIN = GROUP.registerVarargEvent("login", SOURCE, RE_LOGIN, AUTH_TYPE, SERVER_OFFERING)

  override fun getGroup(): EventLogGroup = GROUP

  fun login(loginData: GHLoginData) {
    LOGIN.log(
      SOURCE.with(loginData.loginSource),
      RE_LOGIN.with(loginData.reLogin),
      AUTH_TYPE.with(loginData.authType),
      SERVER_OFFERING.with(loginData.serverOffering)
    )
  }
}

enum class GHAuthType {
  OAUTH,
  TOKEN
}

enum class GHLoginSource {
  GIT,
  CLONE,
  SHARE,
  PR_TW,
  PR_LIST,
  PR_DETAILS,
  PR_CHANGES,
  PR_TIMELINE,
  SETTINGS,
  GIST,
  SYNC_FORK,
  TRACKER,
  WELCOME_SCREEN,
  UNKNOWN;
}

enum class GHServerOffering {
  GITHUB_COM,

  /**
   * GitHub Enterprise Cloud with data residency
   */
  GHE_CLOUD,

  GHE_SERVER;

  companion object {
    fun of(serverPath: GithubServerPath): GHServerOffering = when {
      serverPath.isGithubDotCom -> GITHUB_COM
      serverPath.isGheDataResidency -> GHE_CLOUD
      else -> GHE_SERVER
    }
  }
}

data class GHLoginData(val loginSource: GHLoginSource,
                       val reLogin: Boolean = false,
                       val authType: GHAuthType = GHAuthType.OAUTH,
                       val serverOffering: GHServerOffering = GHServerOffering.GITHUB_COM
) {
  companion object {
    fun GHLoginData.reLogin(): GHLoginData = copy(reLogin = true)
  }
}


