// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import kotlinx.coroutines.flow.MutableStateFlow

// Used only for Java code, since MutableStateFlow function cannot be called there.
internal fun <T> createMutableStateFlow(initialValue: T): MutableStateFlow<T> {
  return MutableStateFlow(initialValue)
}