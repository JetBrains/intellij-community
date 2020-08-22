// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.NullableFunction
import com.intellij.util.PairConsumer

class CommitContext : UserDataHolderBase() {
  private val _additionalData = HashMap<Any, Any>()

  @Deprecated("Use CommitContext")
  val additionalDataConsumer: PairConsumer<Any, Any> = PairConsumer { key, value -> _additionalData[key] = value }

  @Deprecated("Use CommitContext")
  val additionalData: NullableFunction<Any, Any> = NullableFunction { key -> _additionalData[key] }
}
