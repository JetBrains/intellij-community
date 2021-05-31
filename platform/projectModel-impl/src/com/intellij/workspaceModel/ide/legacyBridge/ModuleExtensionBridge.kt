// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.ModuleExtension
import org.jdom.Element

abstract class ModuleExtensionBridge : ModuleExtension() {
  final override fun getModifiableModel(writable: Boolean): ModuleExtension {
    throw UnsupportedOperationException("This method must not be called for extensions backed by workspace model")
  }

  final override fun commit() = Unit
  final override fun dispose() = Unit

  final override fun readExternal(element: Element) {
    throw UnsupportedOperationException("This method must not be called for extensions backed by workspace model")
  }

  final override fun writeExternal(element: Element) {
    throw UnsupportedOperationException("This method must not be called for extensions backed by workspace model")
  }
}