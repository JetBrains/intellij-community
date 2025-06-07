// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.data

/**
 * Access level to a resource
 */
enum class GitLabAccessLevel {
  /**
   * Developer access.
   * Value: 30
   */
  DEVELOPER,

  /**
   * Guest access.
   * Value: 10
   */
  GUEST,

  /**
   * Maintainer access.
   * Value: 40
   */
  MAINTAINER,

  /**
   * Minimal access.
   * Value: 5
   */
  MINIMAL_ACCESS,

  /**
   * No access.
   * Value: 0
   */
  NO_ACCESS,

  /**
   * Owner access.
   * Value: 50
   */
  OWNER,

  /**
   * Reporter access.
   * Value: 20
   */
  REPORTER
}