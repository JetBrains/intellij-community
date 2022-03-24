// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.NullableFunction
import com.intellij.util.PairConsumer

/**
 * Contains specific commit data used in the commit flow. For example:
 * - if "amend commit" should be performed instead of usual commit
 * - if "commit and push" was invoked and so push should start right after commit
 *
 * @see com.intellij.openapi.vcs.checkin.CheckinHandler
 * @see com.intellij.openapi.vcs.changes.CommitExecutor
 * @see com.intellij.openapi.vcs.changes.CommitSession
 * @see com.intellij.openapi.vcs.checkin.CheckinEnvironment
 */
class CommitContext : UserDataHolderBase() {
  private val _additionalData = HashMap<Any, Any>()

  @Deprecated("Use CommitContext")
  val additionalDataConsumer: PairConsumer<Any, Any> = PairConsumer { key, value -> _additionalData[key] = value }

  @Deprecated("Use CommitContext")
  val additionalData: NullableFunction<Any, Any> = NullableFunction { key -> _additionalData[key] }
}
