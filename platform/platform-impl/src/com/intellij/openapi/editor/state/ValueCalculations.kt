// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

fun interface SyncDefaultValueCalculator<T> {
  fun calculate(): T
}

class FixedDefaultValue<T>(private val value: T) : SyncDefaultValueCalculator<T> {
  override fun calculate(): T = value
}

fun interface CustomOutValueModifier <T> {
  fun modifyOutValue(calculated: T): T
}