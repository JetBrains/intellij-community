// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.util.registry.Registry

internal class ExperimentalSoftWrapTest : SoftWrapTest() {
  override fun setUp() {
    super.setUp()
    Registry.get("editor.use.new.soft.wraps.impl").setValue(true, getTestRootDisposable())
  }
}
