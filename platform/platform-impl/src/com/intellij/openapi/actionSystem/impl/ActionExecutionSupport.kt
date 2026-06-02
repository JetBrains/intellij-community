// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet",
               "ReplacePutWithAssignment",
               "ReplaceJavaStaticMethodWithKotlinAnalog",
               "OVERRIDE_DEPRECATION",
               "RemoveRedundantQualifierName")

package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.keymap.impl.ActionProcessor
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.awaitFocusSettlesDown
import com.intellij.util.concurrency.ChildContext
import com.intellij.util.concurrency.createChildContext
import com.intellij.util.ui.StartupUiUtil.addAwtListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import javax.swing.JLabel

@Suppress("DEPRECATION")
internal fun doPerformAction(
  action: AnAction,
  event: AnActionEvent,
  callback: ActionCallback,
) {
  (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
    addAwtListener(AWTEvent.WINDOW_EVENT_MASK, callback) {
      if (it.id == WindowEvent.WINDOW_OPENED || it.id == WindowEvent.WINDOW_ACTIVATED) {
        if (!callback.isProcessed) {
          val we = it as WindowEvent
          IdeFocusManager.findInstanceByComponent(we.window).doWhenFocusSettlesDown(
            callback.createSetDoneRunnable(), ModalityState.defaultModalityState())
        }
      }
    }
    var result = AnActionResult.IGNORED
    try {
      result = ActionUtil.performAction(action, event)
    }
    finally {
      if (result is AnActionResult.Ignored) callback.reject(result.reason)
      else callback.setDone()
    }
  }
}

/**
 * Synchronously updates and executes an action with a custom data context.
 *
 * @param action the action to execute
 * @param place the action place (e.g., "MainMenu", "EditorPopup")
 * @param contextComponent the component to use as context, may be null
 * @param inputEvent the input event that triggered the action, may be null
 * @param callback the callback to be notified of execution result
 * @param dataContext the custom data context to use for update and execution
 *
 * @see ActionManager.tryToExecute for the public API that creates its own DataContext
 */
@ApiStatus.Internal
fun tryToExecuteNow(
  action: AnAction,
  place: String,
  contextComponent: Component?,
  inputEvent: InputEvent?,
  callback: ActionCallback,
  dataContext: DataContext,
) {
  val presentationFactory = PresentationFactory()
  val wrappedContext = Utils.createAsyncDataContext(dataContext)
  val actionProcessor = object : ActionProcessor() {}
  val inputEventAdjusted = inputEvent ?: KeyEvent(
    contextComponent ?: JLabel(), KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_UNDEFINED, '\u0000')
  val updateEvent = Utils.runUpdateSessionForInputEvent(
    listOf(action), inputEventAdjusted, wrappedContext, place, actionProcessor, presentationFactory) { _, updater, events ->
    val presentation = updater(action)
    events[presentation]
  }
  if (updateEvent == null || !updateEvent.presentation.isEnabled) {
    callback.reject("action is disabled (early check)")
    return
  }

  doPerformAction(action, updateEvent, callback)
}

internal suspend fun tryToExecuteSuspend(
  action: AnAction,
  place: String,
  contextComponent: Component?,
  inputEvent: InputEvent?,
  actionManager: ActionManagerImpl,
  callback: ActionCallback,
) {
  (if (contextComponent != null) IdeFocusManager.findInstanceByComponent(contextComponent)
  else IdeFocusManager.getGlobalInstance()).awaitFocusSettlesDown()

  @Suppress("DEPRECATION")
  val dataContext = DataManager.getInstance().let {
    if (contextComponent == null) it.dataContext else it.getDataContext(contextComponent)
  }
  val wrappedContext = Utils.createAsyncDataContext(dataContext)

  val uiKind = ActionUiKind.NONE
  val presentationFactory = PresentationFactory()
  Utils.expandActionGroupSuspend(DefaultActionGroup(action), presentationFactory, wrappedContext, place, uiKind, false)
  val presentation = presentationFactory.getPresentation(action)
  if (!presentation.isEnabled) {
    callback.reject("action is disabled (early check)")
    return
  }

  val event = AnActionEvent(wrappedContext, presentation, place, uiKind, inputEvent, 0, actionManager)

  //todo fix all clients and move locks into them
  runOnEdtWithConditionalWriteIntentSuspending(action) {
    doPerformAction(action, event, callback)
  }
}

internal suspend fun runOnEdtWithConditionalWriteIntentSuspending(action: AnAction, computation: suspend () -> Unit) {
  val dispatcher = if (Utils.isLockRequired(action)) {
    Dispatchers.EDT
  }
  else {
    Dispatchers.UI
  }
  withContext(dispatcher) {
    computation()
  }
}

internal class CapturingListener(@JvmField val timerListener: TimerListener) : TimerListener by timerListener {
  val childContext: ChildContext = createChildContext("ActionManager: $timerListener")

  override fun run() {
    // this is periodic runnable that is invoked on timer; it should not complete a parent job
    childContext.runInChildContext(completeOnFinish = false) {
      timerListener.run()
    }
  }
}

internal fun runListenerAction(listener: TimerListener) {
  val modalityState = listener.modalityState ?: return
  actionManagerImplLog.debug { "notify $listener" }
  if (ModalityState.current().accepts(modalityState)) {
    runCatching {
      listener.run()
    }.getOrLogException(actionManagerImplLog)
  }
}
