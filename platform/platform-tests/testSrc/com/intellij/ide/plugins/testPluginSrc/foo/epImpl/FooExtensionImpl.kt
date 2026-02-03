// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.foo.epImpl

import com.intellij.ide.plugins.testPluginSrc.foo.ep.FooExtension

class FooExtensionImpl : FooExtension {
  override fun foo(): String = "foo!"
}