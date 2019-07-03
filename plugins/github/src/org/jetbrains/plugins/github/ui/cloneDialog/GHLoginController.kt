// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

internal interface GHLoginController {
  fun addAccount()

  fun reLogin(account: GithubAccount)

  fun logout(account: GithubAccount)
}