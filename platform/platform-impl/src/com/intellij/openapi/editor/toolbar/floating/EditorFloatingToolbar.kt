// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.observable.util.addComponent
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

class EditorFloatingToolbar(editor: EditorImpl) : JPanel() {
  init {
    layout = FlowLayout(FlowLayout.RIGHT, 20, 20)
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    FloatingToolbarProvider.EP_NAME.forEachExtensionSafe { provider ->
      addFloatingToolbarComponent(editor, provider)
    }
    FloatingToolbarProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<FloatingToolbarProvider> {
      override fun extensionAdded(extension: FloatingToolbarProvider, pluginDescriptor: PluginDescriptor) {
        addFloatingToolbarComponent(editor, extension)
      }
    }, editor.disposable)
  }

  private fun addFloatingToolbarComponent(editor: EditorImpl, provider: FloatingToolbarProvider) {
    if (provider.isApplicable(editor.dataContext)) {
      val disposable = FloatingToolbarProvider.createExtensionDisposable(provider, editor.disposable)
      val component = FloatingToolbarComponentImpl(editor, provider, disposable)
      addComponent(component, disposable)
    }
  }
}
