// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.util.NullableFunction
import com.intellij.util.PairConsumer
import org.jetbrains.annotations.ApiStatus

@Suppress("DEPRECATION")
@ApiStatus.ScheduledForRemoval
@Deprecated("Use CommitContext")
class PseudoMap<Key, Value> : PairConsumer<Key, Value>, NullableFunction<Key, Value> {
  val commitContext: CommitContext = CommitContext()

  @Suppress("UNCHECKED_CAST")
  override fun `fun`(key: Key): Value? = commitContext.additionalData.`fun`(key) as? Value

  override fun consume(key: Key, value: Value): Unit = commitContext.additionalDataConsumer.consume(key, value)
}
