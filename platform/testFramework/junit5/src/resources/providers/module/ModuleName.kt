// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers.module

import org.jetbrains.annotations.NonNls

/**
 * Each module has a name, and it _must_ be unique!
 */
@JvmInline
value class ModuleName(val name: @NonNls String) {
  constructor() : this("TestModule${Math.random()}")
}