// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.plugins.github.api.GithubServerPath

internal interface GHAccountsHost {
  fun addAccount(server: GithubServerPath, login: String, token: String)
  fun isAccountUnique(login: String, server: GithubServerPath): Boolean

  companion object {
    val KEY: DataKey<GHAccountsHost> = DataKey.create("GHAccountsHost")
  }
}