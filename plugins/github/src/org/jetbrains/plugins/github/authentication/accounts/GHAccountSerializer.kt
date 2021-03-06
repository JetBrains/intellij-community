// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.openapi.components.service

// Helper to hide account ID which is used to store account selection
object GHAccountSerializer {
  fun serialize(account: GithubAccount): String = account.id
  fun deserialize(string: String): GithubAccount? {
    return service<GithubAccountManager>().accounts.find { it.id == string }
  }
}