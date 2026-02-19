// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.foo.ep

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.testFramework.plugins.PluginTestHandle

@Service
class FooExtensionService : PluginTestHandle {
  override fun test() {
    ExtensionPointName.create<FooExtension>(FooExtension.EP_FQN).extensionList.forEach {
      it.foo()
    }
  }
}