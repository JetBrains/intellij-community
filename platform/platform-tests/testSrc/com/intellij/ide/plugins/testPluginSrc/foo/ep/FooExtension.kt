// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.foo.ep

interface FooExtension {
  fun foo(): String

  companion object {
    const val EP_FQN = "com.foo.ep"
  }
}