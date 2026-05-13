// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.testPSC

import com.intellij.platform.testFramework.plugins.PluginTestHandle

internal interface MyPersistentComponent: PluginTestHandle<String?, String?> {
  var data: String?

  override fun test(arg: String?): String? {
    val current = data
    data = arg
    return current
  }
}