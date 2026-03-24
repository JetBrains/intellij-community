// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.beans.PropertyChangeListener
import javax.swing.JComponent


@Internal
object UnknownFileEditor : FileEditor {

  override fun getComponent(): JComponent {
    TODO("not implemented")
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    TODO("not implemented")
  }

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    TODO("not implemented")
  }

  override fun setState(state: FileEditorState) {
    TODO("not implemented")
  }

  override fun isModified(): Boolean {
    TODO("not implemented")
  }

  override fun isValid(): Boolean {
    TODO("not implemented")
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    TODO("not implemented")
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    TODO("not implemented")
  }

  override fun <T> getUserData(key: Key<T?>): T? {
    TODO("not implemented")
  }

  override fun <T> putUserData(key: Key<T?>, value: T?) {
    TODO("not implemented")
  }

  override fun dispose() {
    TODO("not implemented")
  }
}
