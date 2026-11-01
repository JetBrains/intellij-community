// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

class GitLabCredentialsRefreshException(val account: GitLabAccount, cause: Throwable? = null) :
  Exception("Could not refresh credentials for account: `$account`", cause)
