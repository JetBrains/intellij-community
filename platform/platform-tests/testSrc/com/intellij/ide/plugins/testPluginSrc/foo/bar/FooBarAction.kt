// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.foo.bar

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class FooBarAction : AnAction("Foo Bar Action") {
  override fun actionPerformed(e: AnActionEvent) {
    Messages.showInfoMessage("Foo Bar!", "foo.bar")
  }
}