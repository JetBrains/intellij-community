// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.plugins

interface PluginTestHandle<in I, out O> {
  fun test(arg: I): O
}