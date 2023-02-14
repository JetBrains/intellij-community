// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("EventListeners")

package com.intellij.util.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Component
import java.beans.PropertyChangeListener
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkListener

fun Component.addPropertyChangeListener(parent: Disposable, propertyName: String, listener: PropertyChangeListener) {
  addPropertyChangeListener(propertyName, listener)
  Disposer.register(parent) {
    removePropertyChangeListener(propertyName, listener)
  }
}

fun JEditorPane.addHyperLinkListener(parent: Disposable, listener: HyperlinkListener) {
  addHyperlinkListener(listener)
  Disposer.register(parent) {
    removeHyperlinkListener(listener)
  }
}
