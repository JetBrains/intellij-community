// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.moduleService

import com.intellij.openapi.module.Module
import com.intellij.platform.testFramework.plugins.PluginTestHandle

internal class MyModuleService(private val module: Module) : PluginTestHandle<Unit, String> {
  override fun test(arg: Unit): String = module.name
}
