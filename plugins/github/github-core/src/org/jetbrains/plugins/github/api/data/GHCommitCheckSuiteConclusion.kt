// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

enum class GHCommitCheckSuiteConclusion {
  //The check suite or run requires action.
  ACTION_REQUIRED,

  //The check suite or run has been cancelled.
  CANCELLED,

  //The check suite or run has failed.
  FAILURE,

  //The check suite or run was neutral.
  NEUTRAL,

  //The check suite or run was skipped. For internal use only.
  SKIPPED,

  //The check suite or run was marked stale. For internal use only.
  STALE,

  //The check suite or run has failed at startup.
  STARTUP_FAILURE,

  //The check suite or run has succeeded.
  SUCCESS,

  //The check suite or run has timed out.
  TIMED_OUT
}