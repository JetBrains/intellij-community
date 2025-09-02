// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.foo.bar

import com.intellij.platform.testFramework.plugins.PluginTestHandle
import com.intellij.ide.plugins.testPluginSrc.bar.BarData
import com.intellij.ide.plugins.testPluginSrc.bar.BarService
import com.intellij.util.application

class FooBarService : PluginTestHandle {
  fun getBarData(): BarData = application.getService(BarService::class.java).getData()

  override fun test() {
    check(getBarData().x == 42)
  }
}