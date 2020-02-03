// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import git4idea.i18n.GitBundle.getString
import org.jetbrains.annotations.Nls

enum class GitIncomingCheckStrategy(@Nls private val text: String) {

  /**
   * Perform incoming commits checks using Always strategy if no ssh_auth_sock environment variable was detected, Never otherwise
   */
  Auto(getString("settings.git.incoming.change.strategy.text.auto")),

  /**
   * Perform incoming commits checks on remotes after first successful git remote operation
   * and repeat every 20 minutes (time period can be customized via registry key git.update.incoming.info.time)
   */
  Always(getString("settings.git.incoming.change.strategy.text.always")),

  /**
   * Do not perform incoming commits checks.
   */
  Never(getString("settings.git.incoming.change.strategy.text.never"));

  override fun toString() = text
}