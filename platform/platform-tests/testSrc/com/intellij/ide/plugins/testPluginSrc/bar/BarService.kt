// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.bar

import com.intellij.platform.testFramework.plugins.PluginTestHandle

class BarService: PluginTestHandle {
  fun getData(): BarData = data

  override fun test() {
    getData().x
  }

  companion object {
    private val data = BarData(42)
  }
}