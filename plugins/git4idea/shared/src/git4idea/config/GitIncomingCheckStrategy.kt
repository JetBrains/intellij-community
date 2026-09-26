// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

/**
 * This class is preserved only for [GitVcsSettings.setIncomingCommitsCheckStrategy] settings migration.
 */
@Deprecated("Replaced with GitIncomingRemoteCheckStrategy")
internal enum class GitIncomingCheckStrategy {

  /**
   * Perform incoming commits checks using Always strategy if no ssh_auth_sock environment variable was detected, Never otherwise
   */
  Auto,

  /**
   * Perform incoming commits checks on remotes after first successful git remote operation
   * and repeat every 20 minutes (time period can be customized via registry key git.update.incoming.info.time)
   */
  Always,

  /**
   * Do not perform incoming commits checks.
   */
  Never;
}