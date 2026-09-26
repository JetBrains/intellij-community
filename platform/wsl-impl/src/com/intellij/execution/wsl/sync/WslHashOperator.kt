// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

/**
 * Must be kept in sync with wslhash.c.
 */
enum class WslHashOperator(val code: Char) {
  EXCLUDE('-'),
  INCLUDE('+');
}