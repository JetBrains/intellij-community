// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.IJPL207058.module

import com.intellij.ide.plugins.testPluginSrc.IJPL207058.ServiceInterface

class OverriddenService: ServiceInterface {
  override fun foo(): String = "override"
}