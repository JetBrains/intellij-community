// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.Logger
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Rule that logs all actions during the test.
 */
class LogActionsDuringTest: TestWatcher() {

  private val LOG = Logger.getInstance(LogActionsDuringTest::class.java)
  private val actionListener = AnActionListener({ action, dataContext, event ->
    LOG.info("Action: $action (actionId: ${ActionManager.getInstance().getId(action)}); DataContext: $dataContext; Event: $event" )
                                                })

  override fun starting(description: Description) {
    ActionManager.getInstance().addAnActionListener(actionListener)
  }

  override fun finished(description: Description) {
    ActionManager.getInstance().removeAnActionListener(actionListener)
  }

}
