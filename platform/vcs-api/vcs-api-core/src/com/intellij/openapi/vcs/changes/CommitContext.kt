// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.NullableFunction
import com.intellij.util.PairConsumer

/**
 * Contains specific commit data used in the commit flow.
 *
 * It can be used to pass data between
 * [com.intellij.openapi.vcs.changes.CommitExecutor] or [com.intellij.openapi.vcs.checkin.CheckinHandler] and
 * [com.intellij.openapi.vcs.changes.CommitSession] or [com.intellij.openapi.vcs.checkin.CheckinEnvironment].
 *
 * For example:
 * - if "amend commit" should be performed instead of the usual commit (`CommitContext.isAmendCommitMode`).
 * - if "commit and push" was invoked and so push should start right after commit (`CommitContext.isPushAfterCommit`)
 * - if "commit renames separately" mode should be used (`CommitContext.isCommitRenamesSeparately`)
 *
 * @see com.intellij.vcs.commit.commitProperty
 */
class CommitContext : UserDataHolderBase() {
  private val _additionalData = HashMap<Any, Any>()

  /**
   * See [com.intellij.openapi.vcs.checkin.CheckinHandler.beforeCheckin] for (... additionalDataConsumer: PairConsumer<> ...)
   */
  @Deprecated("Use CommitContext")
  val additionalDataConsumer: PairConsumer<Any, Any> = PairConsumer { key, value -> _additionalData[key] = value }

  /**
   * See [com.intellij.openapi.vcs.checkin.CheckinEnvironment.commit] for (... parametersHolder: NullableFunction<> ...)
   */
  @Deprecated("Use CommitContext")
  val additionalData: NullableFunction<Any, Any> = NullableFunction { key -> _additionalData[key] }
}
