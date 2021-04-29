// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinUtils")

package com.intellij.util

inline fun <reified T> Any?.castSafelyTo(): T? = this as? T

inline fun <T> runIf(condition: Boolean, block: () -> T): T? = if (condition) block() else null

inline fun <T> T?.alsoIfNull(block: () -> Unit): T? {
  if (this == null) {
    block()
  }
  return this
}