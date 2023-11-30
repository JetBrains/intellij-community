// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

interface InterpretFilter {
  companion object {
    fun default(): InterpretFilter = object : InterpretFilter {
      override fun shouldCompleteToken(): Boolean = true
    }
  }

  fun shouldCompleteToken(): Boolean
}