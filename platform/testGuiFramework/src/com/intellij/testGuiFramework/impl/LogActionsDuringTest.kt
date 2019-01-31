// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.application.subscribe
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import org.junit.rules.TestWatcher
import org.junit.runner.Description

private val LOG = logger<LogActionsDuringTest>()

/**
 * Rule that logs all actions during the test.
 */
class LogActionsDuringTest : TestWatcher() {
  private var disposable: Disposable? = null

  private val actionListener = object : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
      LOG.info("Action: $action (actionId: ${ActionManager.getInstance().getId(action)}); DataContext: $dataContext; Event: $event")
    }
  }

  override fun starting(description: Description) {
    disposable = Disposer.newDisposable()
    AnActionListener.TOPIC.subscribe(disposable!!, actionListener)
  }

  override fun finished(description: Description) {
    disposable?.let {
      this.disposable = null
      Disposer.dispose(it)
    }
  }
}
