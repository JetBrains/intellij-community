// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EnumEntryName")

package com.intellij.ui.messages

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.ui.Messages.YesNoResult
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.MessageException
import com.intellij.ui.mac.MacMessages
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.util.ui.UIUtil
import com.sun.jna.Callback
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*

internal class MacMessageManagerProviderImpl : MacMessages.MacMessageManagerProvider {
  override fun getMessageManager(): MacMessages {
    if (Registry.`is`("ide.mac.message.sheets.java.emulation.dialogs", true)) {
      return service<JBMacMessages>()
    }
    else {
      return service<NativeMacMessageManager>()
    }
  }
}

private val LOG = Logger.getInstance("#com.intellij.ui.mac.MacMessages")

private class MessageResult(val returnCode: Int, val suppress: Boolean)

@Service
private class NativeMacMessageManager : MacMessages() {
  companion object {
    private val lock = Any()
    private val blockedDocumentRoots = Object2IntOpenHashMap<Window>()
    private val idToWindow = HashMap<Long, Window>()

    private val resultsFromDocumentRoot: MutableMap<Window, MessageResult> = HashMap()
    private val windowToQueue = HashMap<Window, MacMessagesQueue<Runnable>>()

    private val alertDidEnd = object : Callback {
      @Suppress("UNUSED_PARAMETER", "unused")
      fun callback(self: ID?, selector: String?, alert: ID?, returnCode: ID, contextInfo: ID) {
        synchronized(lock) {
          val documentRoot = idToWindow.get(contextInfo.toLong())!!
          processResult(documentRoot)
          val suppressState = Foundation.invoke(Foundation.invoke(alert, "suppressionButton"), "state")
          resultsFromDocumentRoot.put(documentRoot, MessageResult(returnCode.toInt(), suppressState.toInt() == 1))
          windowToQueue.get(idToWindow.get(contextInfo.toLong())!!)!!.runFromQueue()
        }
        Foundation.cfRelease(self)
      }
    }

    private val VARIABLE_BUTTONS_ALERT_PANEL = object : Callback {
      @Suppress("UNUSED_PARAMETER", "unused")
      fun callback(self: ID?, selector: String?, params: ID?) {
        val title = Foundation.invoke(params, "objectAtIndex:", 0)
        val message = Foundation.invoke(params, "objectAtIndex:", 1)
        val focusedWindow = Foundation.invoke(params, "objectAtIndex:", 2)
        val alertStyle = Foundation.invoke(params, "objectAtIndex:", 4)
        val doNotAskText = Foundation.invoke(params, "objectAtIndex:", 5)
        val defaultOptionIndex = Foundation.toStringViaUTF8(Foundation.invoke(params, "objectAtIndex:", 6))!!.toInt()
        //val focusedOptionIndex = Foundation.toStringViaUTF8(Foundation.invoke(params, "objectAtIndex:", 7))!!.toInt()
        val buttons = Foundation.invoke(params, "objectAtIndex:", 8)
        val doNotAskChecked = Foundation.invoke(params, "objectAtIndex:", 9)
        val alert = Foundation.invoke(Foundation.invoke("NSAlert", "alloc"), "init")
        Foundation.invoke(alert, "setMessageText:", title)
        Foundation.invoke(alert, "setInformativeText:", message)
        if ("error" == Foundation.toStringViaUTF8(alertStyle)) {
          // NSCriticalAlertStyle = 2
          Foundation.invoke(alert, "setAlertStyle:", 2)
        }

        val buttonEnumerator = Foundation.invoke(buttons, "objectEnumerator")
        while (true) {
          val button = Foundation.invoke(buttonEnumerator, "nextObject")
          if (button.toInt() == 0) {
            break
          }
          Foundation.invoke(alert, "addButtonWithTitle:", button)
        }
        if (defaultOptionIndex != -1) {
          val button = Foundation.invoke(Foundation.invoke(alert, "buttons"), "objectAtIndex:", defaultOptionIndex)
          Foundation.invoke(Foundation.invoke(alert, "window"), "setDefaultButtonCell:", Foundation.invoke(button, "cell"))
        }

        setAppIcon(alert)

        // it seems like asking for focus will cause java to go and query focus owner too, which may cause dead locks on main-thread
        //if (focusedOptionIndex != -1) {
        //  invoke(invoke(alert, "window"), "makeFirstResponder:",
        //         invoke(invoke(alert, "buttons"), "objectAtIndex:", focusedOptionIndex));
        //} else {
        //  int count = invoke(buttons, "count").intValue();
        //  invoke(invoke(alert, "window"), "makeFirstResponder:",
        //         invoke(invoke(alert, "buttons"), "objectAtIndex:", count == 1 ? 0 : 1));
        //}
        enableEscapeToCloseTheMessage(alert)
        val doNotAsk = Foundation.toStringViaUTF8(doNotAskText)
        if (doNotAsk != "-1") {
          Foundation.invoke(alert, "setShowsSuppressionButton:", 1)
          Foundation.invoke(Foundation.invoke(alert, "suppressionButton"), "setTitle:", doNotAskText)
          Foundation.invoke(Foundation.invoke(alert, "suppressionButton"), "setState:", "checked" == Foundation.toStringViaUTF8(doNotAskChecked))
        }
        Foundation.invoke(alert, "beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:", focusedWindow, self,
                          Foundation.createSelector("alertDidEnd:returnCode:contextInfo:"), focusedWindow)
        Foundation.cfRelease(alert)
      }
    }

    private val SIMPLE_ALERT_PANEL: Callback = object : Callback {
      @Suppress("UNUSED_PARAMETER", "unused")
      fun callback(self: ID?, selector: String?, params: ID?) {
        val title = Foundation.invoke(params, "objectAtIndex:", 0)
        val defaultText = Foundation.invoke(params, "objectAtIndex:", 1)
        val otherText = Foundation.invoke(params, "objectAtIndex:", 2)
        val alternateText = Foundation.invoke(params, "objectAtIndex:", 3)
        val message = Foundation.invoke(params, "objectAtIndex:", 4)
        val focusedWindow = Foundation.invoke(params, "objectAtIndex:", 5)
        val alertStyle = Foundation.invoke(params, "objectAtIndex:", 7)
        val doNotAskText = Foundation.invoke(params, "objectAtIndex:", 8)
        val doNotAskChecked = Foundation.invoke(params, "objectAtIndex:", 9)
        val alternateExist = "-1" != Foundation.toStringViaUTF8(alternateText)
        val otherExist = "-1" != Foundation.toStringViaUTF8(otherText)
        val alert = Foundation.invoke("NSAlert",
                                      "alertWithMessageText:defaultButton:alternateButton:otherButton:informativeTextWithFormat:",
                                      title, defaultText, if (alternateExist) alternateText else null, if (otherExist) otherText else null,
                                      message)
        if (Foundation.toStringViaUTF8(alertStyle) == "error") {
          // NSCriticalAlertStyle = 2
          Foundation.invoke(alert, "setAlertStyle:", 2)
        }

        setAppIcon(alert)

        // it seems like asking for focus will cause java to go and query focus owner too, which may cause dead locks on main-thread
        //ID window = invoke(alert, "window");
        //invoke(window, "makeFirstResponder:",
        //       invoke(invoke(alert, "buttons"), "objectAtIndex:", alternateExist ? 2 : otherExist ? 1 : 0));
        //
        if (!alternateExist) {
          enableEscapeToCloseTheMessage(alert)
        }
        val doNotAsk = Foundation.toStringViaUTF8(doNotAskText)
        if ("-1" != doNotAsk) {
          Foundation.invoke(alert, "setShowsSuppressionButton:", 1)
          Foundation.invoke(Foundation.invoke(alert, "suppressionButton"), "setTitle:", doNotAskText)
          Foundation.invoke(Foundation.invoke(alert, "suppressionButton"), "setState:",
                            "checked" == Foundation.toStringViaUTF8(doNotAskChecked))
        }
        Foundation.invoke(alert, "beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:", focusedWindow, self,
                          Foundation.createSelector("alertDidEnd:returnCode:contextInfo:"), focusedWindow)
      }
    }

    private fun setAppIcon(alert: ID?) {
      Foundation.invoke(alert, "setIcon:",
                        Foundation.invoke(Foundation.invoke("NSApplication", "sharedApplication"), "applicationIconImage"))
    }

    private val windowDidBecomeMainCallback = object : Callback {
      @Suppress("UNUSED_PARAMETER", "unused")
      fun callback(self: ID, nsNotification: ID?) {
        synchronized(lock) {
          if (!idToWindow.keys.contains(self.toLong())) {
            return
          }
        }
        Foundation.invoke(self, "oldWindowDidBecomeMain:", nsNotification)
      }
    }

    init {
      val delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSAlertDelegate_")
      if (!Foundation.addMethod(delegateClass, Foundation.createSelector("alertDidEnd:returnCode:contextInfo:"), alertDidEnd, "v*")) {
        throw RuntimeException("Unable to add method to objective-c delegate class!")
      }
      if (!Foundation.addMethod(delegateClass, Foundation.createSelector("showSheet:"), SIMPLE_ALERT_PANEL, "v*")) {
        throw RuntimeException("Unable to add method to objective-c delegate class!")
      }
      if (!Foundation.addMethod(delegateClass, Foundation.createSelector("showVariableButtonsSheet:"), VARIABLE_BUTTONS_ALERT_PANEL, "v*")) {
        throw RuntimeException("Unable to add method to objective-c delegate class!")
      }
      Foundation.registerObjcClassPair(delegateClass)
        val awtWindow = Foundation.getObjcClass("AWTWindow")
        val windowWillEnterFullScreenMethod = Foundation.createSelector("windowDidBecomeMain:")
        val originalWindowWillEnterFullScreen = Foundation.class_replaceMethod(awtWindow, windowWillEnterFullScreenMethod, windowDidBecomeMainCallback, "v@::@")
        Foundation.addMethodByID(awtWindow, Foundation.createSelector("oldWindowDidBecomeMain:"), originalWindowWillEnterFullScreen, "v@::@")
    }

    private fun processResult(window: Window) {
      synchronized(lock) {
        if (!blockedDocumentRoots.containsKey(window)) {
          throw IllegalStateException("Window should be in th list.")
        }

        val openedAlertsForWindow = blockedDocumentRoots.getInt(window)
        if (openedAlertsForWindow < 1) {
          throw IllegalStateException("Should be at least one window in the list")
        }

        if (openedAlertsForWindow == 1) {
          // the last alert
          blockedDocumentRoots.removeInt(window)
        }
        else {
          blockedDocumentRoots.put(window, openedAlertsForWindow - 1)
        }
      }
    }

    fun pumpEventsDocumentExclusively(documentRoot: Window) {
      val messageNumber = blockedDocumentRoots.getInt(documentRoot)
      val theQueue = documentRoot.toolkit.systemEventQueue
      do {
        try {
          val event = theQueue.nextEvent
          var eventOk = true
          if (event is InputEvent) {
            val source = event.getSource()
            if (source is Component) {
              val window = findDocumentRoot(source)
              if (window === documentRoot) {
                eventOk = false
                event.consume()
              }
            }
          }

          if (eventOk) {
            val method = theQueue.javaClass.getDeclaredMethod("dispatchEvent", AWTEvent::class.java)
            method.isAccessible = true
            method.invoke(theQueue, event)
          }
        }
        catch (e: MessageException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
      while (isBlockedDocumentRoot(documentRoot, messageNumber))
    }

    private fun isBlockedDocumentRoot(documentRoot: Window?, messageNumber: Int?): Boolean {
      synchronized(lock) {
        return messageNumber == blockedDocumentRoots.getInt(documentRoot)
      }
    }

    private fun startModal(window: Window, windowId: ID) {
      val windowPointer = windowId.toLong()
      synchronized(lock) {
        idToWindow.put(windowPointer, window)
        blockedDocumentRoots.addTo(window, 1)
      }
      pumpEventsDocumentExclusively(window)
      synchronized(lock) {
        idToWindow.remove(windowPointer)
      }
    }

    @YesNoCancelResult
    fun showAlertDialog(@NlsContexts.DialogTitle title: String,
                        @NlsContexts.DialogMessage message: String? = null,
                        @NlsContexts.Button yesText: String,
                        @NlsContexts.Button alternateText: String? = null,
                        @NlsContexts.Button noText: String? = null,
                        window: Window? = null,
                        errorStyle: Boolean = false,
                        doNotAskOption: DoNotAskOption? = null): Int {
      val effectiveWindow = window ?: JBMacMessages.getForemostWindow()
      runAndRelease {
        val params = DialogParamsWrapper(DialogType.alert)
        params.add(CommonDialogParamType.title, title)
        params.removeMnemonicAndAdd(AlertDialogParamType.defaultText, yesText)
        params.removeMnemonicAndAdd(AlertDialogParamType.alternateText, noText)
        params.removeMnemonicAndAdd(AlertDialogParamType.otherText, alternateText)
        // replace % -> %% to avoid formatted parameters (causes SIGTERM)
        params.add(CommonDialogParamType.message, StringUtil.stripHtml(message ?: "", true).replace("%", "%%"))
        params.add(CommonDialogParamType.errorStyle, if (errorStyle) "error" else "-1")
        // TODO: state=!doNotAsk.shouldBeShown()
        params.add(CommonDialogParamType.doNotAskDialogOption1, if (doNotAskOption == null || !doNotAskOption.canBeHidden()) "-1" else doNotAskOption.doNotShowMessage)
        params.add(CommonDialogParamType.doNotAskDialogOption2, if (doNotAskOption != null && !doNotAskOption.isToBeShown) "checked" else "-1")

        val result = resultsFromDocumentRoot.remove(showDialog(effectiveWindow, "showSheet:", params))!!
        val convertedResult = convertReturnCodeFromNativeAlertDialog(result.returnCode, alternateText)
        if (doNotAskOption != null && doNotAskOption.canBeHidden()) {
          val operationCanceled = convertedResult == Messages.CANCEL
          if (!operationCanceled || doNotAskOption.shouldSaveOptionsOnCancel()) {
            doNotAskOption.setToBeShown(!result.suppress, convertedResult)
          }
        }
        return convertedResult
      }
    }

    private fun showDialog(window: Window, methodName: String, paramsWrapper: DialogParamsWrapper): Window? {
      val documentRoot = findDocumentRoot(window)
      val nativeFocusedWindow = MacUtil.findWindowFromJavaWindow(window)
      val paramsArray = paramsWrapper.getParamsAsId(nativeFocusedWindow)
      val windowListener = object : WindowAdapter() {
        override fun windowClosed(e: WindowEvent) {
          windowToQueue.remove(documentRoot)
          if (blockedDocumentRoots.containsKey(documentRoot)) {
            blockedDocumentRoots.removeInt(documentRoot)
            throw MessageException("Owner window has been removed")
          }
        }
      }
      window.addWindowListener(windowListener)
      val delegate = Foundation.invoke(Foundation.invoke(Foundation.getObjcClass("NSAlertDelegate_"), "alloc"), "init")
      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(false)
      runOrPostponeForWindow(documentRoot!!, Runnable {
        Foundation.invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:",
                          Foundation.createSelector(methodName), paramsArray, false)
      })
      startModal(documentRoot, nativeFocusedWindow)
      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(true)
      window.removeWindowListener(windowListener)
      return documentRoot
    }

    private fun convertReturnCodeFromNativeMessageDialog(result: Int): Int {
      return result - 1000
    }

    private fun runOrPostponeForWindow(documentRoot: Window, task: Runnable) {
      synchronized(lock) {
        var queue = windowToQueue.get(documentRoot)
        if (queue == null) {
          queue = MacMessagesQueue()
          windowToQueue.put(documentRoot, queue)
        }
        queue.runOrSchedule(task)
      }
    }
  }

  override fun showOkMessageDialog(title: String, message: String, okText: String, window: Window?) {
    showAlertDialog(title = title, message = message, window = window, yesText = okText)
  }

  @YesNoResult
  override fun showYesNoDialog(title: String,
                               message: String,
                               yesText: String,
                               noText: String,
                               window: Window?,
                               doNotAskDialogOption: DoNotAskOption?): Boolean {
    return showAlertDialog(title = title, message = message, yesText = yesText, noText = noText,
                           window = window,
                           doNotAskOption = doNotAskDialogOption) == Messages.YES
  }

  override fun showErrorDialog(title: String, message: String, okButton: String, window: Window?) {
    showAlertDialog(title = title, message = message, yesText = okButton, window = window, errorStyle = true)
  }

  @YesNoCancelResult
  override fun showYesNoCancelDialog(title: @NotNull String,
                                     message: String,
                                     yesText: @NotNull String,
                                     noText: @NotNull String,
                                     cancelText: @NotNull String,
                                     window: @Nullable Window?,
                                     doNotAskOption: @Nullable DoNotAskOption?): Int {
    return showAlertDialog(title = title, message = message,
                           yesText = yesText, alternateText = noText, noText = cancelText,
                           window = window, doNotAskOption = doNotAskOption)
  }

  override fun showMessageDialog(title: String,
                                 message: String,
                                 buttons: Array<String>,
                                 errorStyle: Boolean,
                                 window: Window?,
                                 defaultOptionIndex: Int,
                                 focusedOptionIndex: Int,
                                 doNotAskDialogOption: DoNotAskOption?): Int {
    val effectiveWindow = window ?: JBMacMessages.getForemostWindow()
    runAndRelease {
      val params = DialogParamsWrapper(DialogType.message)
      params.add(CommonDialogParamType.title, title)
      // replace % -> %% to avoid formatted parameters (causes SIGTERM)
      params.add(CommonDialogParamType.message, StringUtil.stripHtml(message, true).replace("%", "%%"))
      params.add(CommonDialogParamType.errorStyle, if (errorStyle) "error" else "-1")
      // TODO: state=!doNotAsk.shouldBeShown()
      params.add(CommonDialogParamType.doNotAskDialogOption1, if (doNotAskDialogOption == null || !doNotAskDialogOption.canBeHidden()) "-1" else doNotAskDialogOption.doNotShowMessage)
      params.add(CommonDialogParamType.doNotAskDialogOption2, if (doNotAskDialogOption != null && !doNotAskDialogOption.isToBeShown) "checked" else "-1")
      params.add(MessageDialogParamType.defaultOptionIndex, defaultOptionIndex.toString())
      params.add(MessageDialogParamType.focusedOptionIndex, focusedOptionIndex.toString())

      val buttonArray = Foundation.invoke("NSMutableArray", "array")
      for (button in buttons) {
        Foundation.invoke(buttonArray, "addObject:", Foundation.nsString(UIUtil.removeMnemonic(button)))
      }
      params.params.put(MessageDialogParamType.buttonArray, buttonArray)

      val result = resultsFromDocumentRoot.remove(showDialog(effectiveWindow, "showVariableButtonsSheet:", params))
      val code = convertReturnCodeFromNativeMessageDialog(result!!.returnCode)
      val cancelCode = buttons.size - 1
      if (doNotAskDialogOption != null && doNotAskDialogOption.canBeHidden()) {
        if (cancelCode != code || doNotAskDialogOption.shouldSaveOptionsOnCancel()) {
          doNotAskDialogOption.setToBeShown(!result.suppress, code)
        }
      }
      return code
    }
  }
}

private inline fun <T> runAndRelease(task: () -> T): T {
  val pool = Foundation.invoke(Foundation.invoke("NSAutoreleasePool", "alloc"), "init")
  try {
    return task()
  }
  finally {
    Foundation.invoke(pool, "release")
  }
}

private enum class CommonDialogParamType {
  title, message, errorStyle, doNotAskDialogOption1, doNotAskDialogOption2, nativeFocusedWindow
}

private enum class DialogType {
  alert, message
}

private enum class MessageDialogParamType {
  buttonArray, defaultOptionIndex, focusedOptionIndex
}

private enum class AlertDialogParamType {
  defaultText, alternateText, otherText
}

private class DialogParamsWrapper(private val dialogType: DialogType) {
  val params = HashMap<Enum<*>, Any>()

  fun add(name: Enum<*>, value: String) {
    params.put(name, Foundation.nsString(value))
  }

  fun removeMnemonicAndAdd(name: Enum<*>, @Nls value: String?) {
    params.put(name, Foundation.nsString(if (value == null) "-1" else UIUtil.removeMnemonic(value)))
  }

  fun getParamsAsId(window: ID): ID? {
    params.put(CommonDialogParamType.nativeFocusedWindow, window)
    return when (dialogType) {
      DialogType.alert -> getParamsForAlertDialog(params)
      DialogType.message -> getParamsForMessageDialog(params)
    }
  }
}

private fun getParamsForAlertDialog(params: Map<Enum<*>, Any>): ID {
  return Foundation.invoke(
    "NSArray", "arrayWithObjects:",
    params[CommonDialogParamType.title],
    params[AlertDialogParamType.defaultText],
    params[AlertDialogParamType.alternateText],
    params[AlertDialogParamType.otherText],
    params[CommonDialogParamType.message],
    params[CommonDialogParamType.nativeFocusedWindow],
    Foundation.nsString(""),
    params[CommonDialogParamType.errorStyle],
    params[CommonDialogParamType.doNotAskDialogOption1],
    params[CommonDialogParamType.doNotAskDialogOption2],
    null
  )
}

private fun getParamsForMessageDialog(params: Map<Enum<*>, Any>): ID {
  return Foundation.invoke(
    "NSArray", "arrayWithObjects:",
    params[CommonDialogParamType.title],
    params[CommonDialogParamType.message],
    params[CommonDialogParamType.nativeFocusedWindow],
    Foundation.nsString(""),
    params[CommonDialogParamType.errorStyle],
    params[CommonDialogParamType.doNotAskDialogOption1],
    params[MessageDialogParamType.defaultOptionIndex],
    params[MessageDialogParamType.focusedOptionIndex],
    params[MessageDialogParamType.buttonArray],
    params[CommonDialogParamType.doNotAskDialogOption2],
    null
  )
}

private fun enableEscapeToCloseTheMessage(alert: ID) {
  val buttonCount = Foundation.invoke(Foundation.invoke(alert, "buttons"), "count").toInt()
  if (buttonCount < 2) {
    return
  }
  val button = Foundation.invoke(Foundation.invoke(alert, "buttons"), "objectAtIndex:", buttonCount - 1)
  Foundation.invoke(button, "setKeyEquivalent:", Foundation.nsString("\u001b"))
}

@YesNoCancelResult
private fun convertReturnCodeFromNativeAlertDialog(returnCode: Int, alternateText: String?): Int {
  // DEFAULT = 1
  // ALTERNATE = 0
  // OTHER = -1 (cancel)
  val cancelCode: Int
  var code: Int
  if (alternateText != null) {
    // DEFAULT = 0
    // ALTERNATE = 1
    // CANCEL = 2
    cancelCode = Messages.CANCEL
    code = when (returnCode) {
      1 -> Messages.YES
      0 -> Messages.NO
      -1 -> Messages.CANCEL
      else -> Messages.CANCEL
    }
  }
  else {
    // DEFAULT = 0
    // CANCEL = 1
    cancelCode = 1
    code = when (returnCode) {
      1 -> Messages.YES
      -1 -> Messages.NO
      else -> Messages.NO
    }
  }

  if (cancelCode == code) {
    code = Messages.CANCEL
  }

  LOG.assertTrue(code == Messages.YES || code == Messages.NO || code == Messages.CANCEL, code)
  return code
}

private fun findDocumentRoot(component: Component): Window? {
  var window = ComponentUtil.getWindow(component)!!
  synchronized(component.treeLock) {
    while (window.owner != null) {
      window = window.owner
    }
  }
  return window
}

private class MacMessagesQueue<T : Runnable> {
  private var waitingForAppKit = false
  private val queue = ArrayDeque<Runnable>()

  @Synchronized
  fun runOrSchedule(runnable: T) {
    if (waitingForAppKit) {
      queue.add(runnable)
    }
    else {
      runnable.run()
      waitingForAppKit = true
    }
  }

  @Synchronized
  fun runFromQueue() {
    val task = queue.pollFirst()
    if (task == null) {
      waitingForAppKit = false
    }
    else {
      task.run()
      waitingForAppKit = true
    }
  }
}