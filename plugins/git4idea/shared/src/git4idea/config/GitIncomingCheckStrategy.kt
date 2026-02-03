// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

enum class GitIncomingCheckStrategy(@Nls @PropertyKey(resourceBundle = GitBundle.BUNDLE) private val text: String) {

  /**
   * Perform incoming commits checks using Always strategy if no ssh_auth_sock environment variable was detected, Never otherwise
   */
  Auto("settings.git.incoming.change.strategy.text.auto"),

  /**
   * Perform incoming commits checks on remotes after first successful git remote operation
   * and repeat every 20 minutes (time period can be customized via registry key git.update.incoming.info.time)
   */
  Always("settings.git.incoming.change.strategy.text.always"),

  /**
   * Do not perform incoming commits checks.
   */
  Never("settings.git.incoming.change.strategy.text.never");

  override fun toString(): String = GitBundle.message(text)
}