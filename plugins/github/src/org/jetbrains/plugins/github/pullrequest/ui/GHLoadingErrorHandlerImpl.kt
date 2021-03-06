// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

@Deprecated("Deprecated in favor of more concrete class",
            replaceWith = ReplaceWith("GHApiLoadingErrorHandler",
                                      "org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler"))
class GHLoadingErrorHandlerImpl(project: Project,
                                account: GithubAccount,
                                resetRunnable: () -> Unit)
  : GHApiLoadingErrorHandler(project, account, resetRunnable)