// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EnumEntryName")

package com.intellij.ui.messages

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.mac.MacMessages
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.util.ui.UIUtil
import com.sun.jna.Callback
import java.awt.SecondaryLoop
import java.awt.Toolkit
import java.awt.Window

internal class MacMessageManagerProviderImpl : MacMessages.MacMessageManagerProvider {
  override fun getMessageManager(): MacMessages {
    if (SystemInfo.isJetBrainsJvm) {
      if (SystemInfo.isMacOSBigSur) {
        if (Registry.`is`("ide.mac.bigsur.alerts.enabled", true)) {
          return service<NativeMacMessageManager>()
        }
        return service<JBMacMessages>()
      }
      if (!Registry.`is`("ide.mac.message.sheets.java.emulation.dialogs", true)) {
        return service<NativeMacMessageManager>()
      }
    }
    return service<JBMacMessages>()
  }
}

private class MessageInfo(val title: String,
                          message: String?,
                          val buttons: Array<String>,
                          val errorStyle: Boolean,
                          window: Window?,
                          val defaultOptionIndex: Int,
                          val doNotAskDialogOption: DoNotAskOption?) {
  val message = StringUtil.stripHtml(message ?: "", true).replace("%", "%%")
  val window = window ?: JBMacMessages.getForemostWindow()
  val nativeWindow: ID = MacUtil.findWindowFromJavaWindow(this.window)
}

@Service
private class NativeMacMessageManager : MacMessages() {
  private var myInfo: MessageInfo? = null
  private var myLoop: SecondaryLoop? = null
  private var myResult: Int? = null
  private var mySuppress: Boolean = false

  private fun getJBMessages() = service<JBMacMessages>()

  override fun showYesNoCancelDialog(title: String,
                                     message: String,
                                     yesText: String,
                                     noText: String,
                                     cancelText: String,
                                     window: Window?,
                                     doNotAskOption: DoNotAskOption?): Int {
    return showMessageDialog(title, message, arrayOf(yesText, noText, cancelText), false, window, -1, doNotAskOption) {
      getJBMessages().showYesNoCancelDialog(title, message, yesText, noText, cancelText, window, doNotAskOption)
    }
  }

  override fun showOkMessageDialog(title: String,
                                   message: String?,
                                   okText: String,
                                   window: Window?) {
    showMessageDialog(title, message, arrayOf(okText), false, window, -1, null) {
      getJBMessages().showOkMessageDialog(title, message, okText, window)
      Messages.YES
    }
  }

  override fun showYesNoDialog(title: String,
                               message: String,
                               yesText: String,
                               noText: String,
                               window: Window?,
                               doNotAskDialogOption: DoNotAskOption?): Boolean {
    return showMessageDialog(title, message, arrayOf(yesText, noText), false, window, -1, doNotAskDialogOption) {
      if (getJBMessages().showYesNoDialog(title, message, yesText, noText, window, doNotAskDialogOption)) {
        Messages.YES
      }
      else {
        Messages.NO
      }
    } == Messages.YES
  }

  override fun showErrorDialog(title: String,
                               message: String?,
                               okButton: String,
                               window: Window?) {
    showMessageDialog(title, message, arrayOf(okButton), true, window, -1, null) {
      getJBMessages().showErrorDialog(title, message, okButton, window)
      Messages.OK
    }
  }

  override fun showMessageDialog(title: String,
                                 message: String?,
                                 buttons: Array<String>,
                                 errorStyle: Boolean,
                                 window: Window?,
                                 defaultOptionIndex: Int,
                                 focusedOptionIndex: Int,
                                 doNotAskDialogOption: DoNotAskOption?): Int {
    return showMessageDialog(title, message, buttons, errorStyle, window, defaultOptionIndex, doNotAskDialogOption) {
      getJBMessages().showMessageDialog(title, message, buttons, errorStyle, window, defaultOptionIndex, focusedOptionIndex,
                                        doNotAskDialogOption)
    }
  }

  @Messages.YesNoCancelResult
  private fun showMessageDialog(@NlsContexts.DialogTitle title: String,
                                @NlsContexts.DialogMessage message: String?,
                                buttons: Array<@NlsContexts.Button String>,
                                errorStyle: Boolean,
                                window: Window?,
                                defaultOptionIndex: Int,
                                doNotAskDialogOption: DoNotAskOption?,
                                fallback: () -> Int): Int {
    val info = MessageInfo(title, message, buttons, errorStyle, window, defaultOptionIndex, doNotAskDialogOption)

    assert(info.window.isVisible)
    assert(myInfo == null)

    myInfo = info
    myResult = null
    myLoop = Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()

    try {
      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(false)

      val delegate = Foundation.invoke(Foundation.invoke(Foundation.getObjcClass("NSJavaAlertDelegate"), "alloc"), "init")
      Foundation.invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:", Foundation.createSelector("showAlert:"), ID.NIL,
                        false)
      myLoop!!.enter()
      Foundation.cfRelease(delegate)
    }
    finally {
      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(true)
      myInfo = null
      myLoop = null
    }

    if (myResult != null) {
      val result = myResult!! - 1000

      if (doNotAskDialogOption != null && doNotAskDialogOption.canBeHidden()) {
        if (result != Messages.CANCEL || doNotAskDialogOption.shouldSaveOptionsOnCancel()) {
          doNotAskDialogOption.setToBeShown(!mySuppress, result)
        }
      }

      return result
    }

    return fallback()
  }

  private val SHOW_ALERT = object : Callback {
    @Suppress("UNUSED_PARAMETER", "unused")
    fun callback(self: ID, selector: String, params: ID) {
      val info = myInfo!!

      val window = getActualWindow(info.nativeWindow)
      if (window == null) {
        myLoop!!.exit()
        return
      }

      val alert = Foundation.invoke(Foundation.invoke("NSAlert", "alloc"), "init")

      Foundation.invoke(alert, "setMessageText:", Foundation.nsString(info.title))
      Foundation.invoke(alert, "setInformativeText:", Foundation.nsString(info.message))

      val app = Foundation.invoke("NSApplication", "sharedApplication")
      Foundation.invoke(alert, "setIcon:", Foundation.invoke(app, "applicationIconImage"))

      if (info.errorStyle) {
        Foundation.invoke(alert, "setAlertStyle:", /*NSCriticalAlertStyle*/2)
      }

      var enableEscape = true

      for (button in info.buttons) {
        val nsButton = Foundation.invoke(alert, "addButtonWithTitle:", Foundation.nsString(UIUtil.removeMnemonic(button)))
        // don't equals with nls "button.cancel"
        if (button == "Cancel") {
          Foundation.invoke(nsButton, "setKeyEquivalent:", Foundation.nsString("\u001b"))
          enableEscape = false
        }
      }

      if (enableEscape) {
        enableEscapeToCloseTheMessage(alert)
      }

      if (info.doNotAskDialogOption != null && info.doNotAskDialogOption.canBeHidden()) {
        Foundation.invoke(alert, "setShowsSuppressionButton:", 1)

        val button = Foundation.invoke(alert, "suppressionButton")
        Foundation.invoke(button, "setTitle:", Foundation.nsString(info.doNotAskDialogOption.doNotShowMessage))
        Foundation.invoke(button, "setState:", !info.doNotAskDialogOption.isToBeShown)
      }

      if (info.defaultOptionIndex in info.buttons.indices) {
        val button = Foundation.invoke(Foundation.invoke(alert, "buttons"), "objectAtIndex:", info.defaultOptionIndex)
        Foundation.invoke(Foundation.invoke(alert, "window"), "setDefaultButtonCell:", Foundation.invoke(button, "cell"))
      }

      Foundation.invoke(alert, "beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:", window, self,
                        Foundation.createSelector("alertDidEnd:returnCode:contextInfo:"), ID.NIL)
      Foundation.cfRelease(alert)
    }
  }

  private val ALERT_DID_END = object : Callback {
    @Suppress("UNUSED_PARAMETER", "unused")
    fun callback(self: ID, selector: String, alert: ID, returnCode: ID, contextInfo: ID) {
      myResult = returnCode.toInt()
      mySuppress = Foundation.invoke(Foundation.invoke(alert, "suppressionButton"), "state").toInt() == 1
      myLoop!!.exit()
    }
  }

  init {
    val delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSJavaAlertDelegate")
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("showAlert:"), SHOW_ALERT, "v*")) {
      throw RuntimeException("Unable to add `showAlert:` method to Objective-C NSJavaAlertDelegate class")
    }
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("alertDidEnd:returnCode:contextInfo:"), ALERT_DID_END, "v*")) {
      throw RuntimeException("Unable to add `alertDidEnd:returnCode:contextInfo:` method to Objective-C NSJavaAlertDelegate class")
    }
    Foundation.registerObjcClassPair(delegateClass)
  }
}

private fun getActualWindow(window: ID): ID? {
  if (!Foundation.invoke(window, "isVisible").booleanValue() || ID.NIL.equals(Foundation.invoke(window, "screen"))) {
    val parent = Foundation.invoke(window, "parent")
    if (ID.NIL.equals(parent)) {
      return null
    }
    return getActualWindow(parent)
  }
  return window
}

private fun enableEscapeToCloseTheMessage(alert: ID) {
  val buttonCount = Foundation.invoke(Foundation.invoke(alert, "buttons"), "count").toInt()
  if (buttonCount > 1) {
    val button = Foundation.invoke(Foundation.invoke(alert, "buttons"), "objectAtIndex:", buttonCount - 1)
    Foundation.invoke(button, "setKeyEquivalent:", Foundation.nsString("\u001b"))
  }
}