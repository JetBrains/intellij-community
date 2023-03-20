// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

/**
 * A marker interface for GitLab files
 */
interface GitLabVirtualFile {
  /**
   * ID of the connection for which the file was opened
   */
  val connectionId: String
}