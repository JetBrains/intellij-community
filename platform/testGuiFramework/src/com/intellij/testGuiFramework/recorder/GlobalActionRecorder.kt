// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.testGuiFramework.recorder

import com.intellij.application.subscribe
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.testGuiFramework.recorder.ui.GuiScriptEditorPanel
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

object GlobalActionRecorder {
  private val LOG = logger<GlobalActionRecorder>()

  private var disposable: Disposable? = null

  var isActive: Boolean = false
    private set

  private val globalActionListener = object : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
      if (event.place == GuiScriptEditorPanel.GUI_SCRIPT_EDITOR_PLACE) return //avoid GUI Script Editor Actions
      EventDispatcher.processActionEvent(action, event)
      LOG.info("IDEA is going to perform action ${action.templatePresentation.text}")
    }

    override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
      LOG.info("IDEA typing detected: ${c}")
    }

    override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
      if (event.place == GuiScriptEditorPanel.GUI_SCRIPT_EDITOR_PLACE) return //avoid GUI Script Editor Actions
      LOG.info("IDEA action performed ${action.templatePresentation.text}")
    }
  }

  private val globalAwtProcessor = IdeEventQueue.EventDispatcher { awtEvent ->
    try {
      when (awtEvent) {
        is MouseEvent -> EventDispatcher.processMouseEvent(awtEvent)
        is KeyEvent -> EventDispatcher.processKeyBoardEvent(awtEvent)
      }
    }
    catch (e: Exception) {
      LOG.warn(e)
    }
    false
  }

  fun activate() {
    if (isActive) return
    LOG.info("Global action recorder is active")
    disposable = Disposer.newDisposable()
    AnActionListener.TOPIC.subscribe(disposable!!, globalActionListener)
    IdeEventQueue.getInstance().addDispatcher(globalAwtProcessor, disposable) //todo: add disposal dependency on component
    isActive = true
  }

  fun deactivate() {
    if (isActive) {
      LOG.info("Global action recorder is non active")
      disposable?.let {
        this.disposable = null
        Disposer.dispose(it)
      }
    }
    isActive = false
    ContextChecker.clearContext()
  }
}