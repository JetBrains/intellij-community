// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

/**
 * Status of the job.
 */
enum class GitLabCiJobStatus {
  /**
   * A job that is canceled.
   */
  CANCELED,

  /**
   * A job that is created.
   */
  CREATED,

  /**
   * A job that is failed.
   */
  FAILED,

  /**
   * A job that is manual.
   */
  MANUAL,

  /**
   * A job that is pending.
   */
  PENDING,

  /**
   * A job that is preparing.
   */
  PREPARING,

  /**
   * A job that is running.
   */
  RUNNING,

  /**
   * A job that is scheduled.
   */
  SCHEDULED,

  /**
   * A job that is skipped.
   */
  SKIPPED,

  /**
   * A job that is success.
   */
  SUCCESS,

  /**
   * A job that is waiting for resource.
   */
  WAITING_FOR_RESOURCE
}