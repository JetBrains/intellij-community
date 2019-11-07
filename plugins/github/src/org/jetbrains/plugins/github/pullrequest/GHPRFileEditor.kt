// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

internal class GHPRFileEditor(private val name: String,
                              componentSupplier: () -> ComponentContainer)
  : UserDataHolderBase(), FileEditor {

  private val propertyChangeSupport = PropertyChangeSupport(this)
  private val container by lazy(LazyThreadSafetyMode.NONE) {
    componentSupplier().also {
      Disposer.register(this, it)
    }
  }

  override fun getName(): String = name

  override fun getComponent(): JComponent = container.component
  override fun getPreferredFocusedComponent(): JComponent? = container.preferredFocusableComponent

  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true

  override fun selectNotify() {
    val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Update")
    ActionUtil.invokeAction(action, component, ActionPlaces.UNKNOWN, null, null)
  }

  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = propertyChangeSupport.addPropertyChangeListener(listener)
  override fun removePropertyChangeListener(listener: PropertyChangeListener) = propertyChangeSupport.removePropertyChangeListener(listener)

  override fun setState(state: FileEditorState) {}
  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

  override fun getCurrentLocation(): FileEditorLocation? = null

  override fun dispose() {}
}
