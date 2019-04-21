// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.util.NullableFunction
import com.intellij.util.PairConsumer

class PseudoMap<Key, Value> : PairConsumer<Key, Value>, NullableFunction<Key, Value> {
  private val myMap = mutableMapOf<Key, Value>()

  override fun `fun`(key: Key): Value? = myMap[key]

  override fun consume(key: Key, value: Value) {
    myMap[key] = value
  }
}
