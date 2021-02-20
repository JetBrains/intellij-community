// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EnumEntryName")

package com.intellij.ui.messages

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.StackingPopupDispatcher
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.mac.MacMessages
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.util.ReflectionUtil
import com.intellij.util.ui.UIUtil
import com.sun.jna.Callback
import java.awt.*
import java.awt.event.WindowEvent
import java.lang.reflect.Proxy
import javax.swing.JDialog
import javax.swing.SwingUtilities

private val LOG = Logger.getInstance(MacMessages::class.java)

internal class MacMessageManagerProviderImpl : MacMessages.MacMessageManagerProvider {
  override fun getMessageManager(): MacMessages {
    return getLocalMacMessages()
  }
}

fun getLocalMacMessages(): MacMessages {
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

private class MessageInfo(val title: String,
                          message: String?,
                          val buttons: Array<String>,
                          val errorStyle: Boolean,
                          window: Window?,
                          val defaultOptionIndex: Int,
                          val doNotAskDialogOption: DoNotAskOption?) {
  val message = MacMessageHelper.stripHtmlMessage(message)

  val window: Window
  val visibleWindow: Window
  val nativeWindow: ID
  var mainHandler = {}

  init {
    var popupWindow: Window? = null
    val popup = StackingPopupDispatcher.getInstance().focusedPopup
    if (popup != null) {
      popupWindow = SwingUtilities.getWindowAncestor(popup.content)
    }
    if (popupWindow == null) {
      this.window = window ?: JBMacMessages.getForemostWindow()
    }
    else {
      this.window = popupWindow
    }
    this.visibleWindow = getVisibleWindow(this.window)
    this.nativeWindow = MacUtil.getWindowFromJavaWindow(this.visibleWindow)
  }
}

private fun getVisibleWindow(window: Window): Window {
  if (!window.isVisible) {
    val parent = window.parent
    if (parent is Window) {
      return getVisibleWindow(parent)
    }
  }
  return window
}

class MacMessageHelper {
  companion object {
    @JvmStatic
    fun stripHtmlMessage(message: String?): String {
      if (message == null) {
        return ""
      }
      var result: String = message
      while (true) {
        val start = StringUtil.indexOf(result, "<style>", 0)
        if (start == -1) {
          break
        }
        val end = StringUtil.indexOf(result, "</style>", start + 7)
        if (end == -1) {
          break
        }
        result = result.substring(0, start) + result.substring(end + 8)
      }
      return StringUtil.unescapeXmlEntities(StringUtil.stripHtml(result, "\n")).replace("%", "%%").replace("&nbsp;", " ")
    }
  }
}

@Service
private class NativeMacMessageManager : MacMessages() {
  private var myInfo: MessageInfo? = null
  private var myDialog: MyDialog? = null
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
    ApplicationManager.getApplication().assertIsDispatchThread()
    val info = MessageInfo(title, message, buttons, errorStyle, window, defaultOptionIndex, doNotAskDialogOption)

    if (myInfo != null) {
      LOG.error("=== MacAlert: attempt to show new alert during showing another alert ===")
      return fallback()
    }

    info.mainHandler = {
      Foundation.invoke(Foundation.invoke(info.nativeWindow, "delegate"), "activateWindowMenuBar")
      myDialog!!.orderAboveSiblings()

      info.mainHandler = {
        myDialog!!.orderAboveSiblings()
      }
    }

    myInfo = info
    myResult = null

    var disposer = {}

    myDialog = MyDialog(info.window) {
      val delegate = Foundation.invoke(Foundation.invoke(Foundation.getObjcClass("NSJavaAlertDelegate"), "alloc"), "init")
      Foundation.invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:", Foundation.createSelector("showAlert:"), ID.NIL,
                        false)
      disposer = {
        Foundation.cfRelease(delegate)
        Foundation.executeOnMainThread(false, false) {
          Foundation.invoke(Foundation.invoke(info.nativeWindow, "delegate"), "activateWindowMenuBar")
        }
      }
    }

    try {
      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(false)
      StackingPopupDispatcher.getInstance().hidePersistentPopups()
      myDialog!!.show()
    }
    finally {
      disposer()
      StackingPopupDispatcher.getInstance().restorePersistentPopups()
      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(true)
      myInfo = null
      myDialog = null
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

    LOG.error("=== MacAlert: no return value from native ===")
    return fallback()
  }

  private fun ourParentIsTopLevelWindowWithoutChildren(): Boolean {
    val window = myInfo!!.visibleWindow
    return window.parent == null && window.ownedWindows.all { it == myDialog || !it.isVisible }
  }

  private val SHOW_ALERT = object : Callback {
    @Suppress("UNUSED_PARAMETER", "unused")
    fun callback(self: ID, selector: String, params: ID) {
      val info = myInfo!!
      val ownerWindow = getActualWindow(info.nativeWindow)
      val runModal = ownerWindow == null || !ourParentIsTopLevelWindowWithoutChildren()  /* prevent z-order issues with other children of our parent */

      val alert = Foundation.invoke(Foundation.invoke("NSAlert", "alloc"), "init")
      val alertWindow = Foundation.invoke(alert, "window")

      if (!runModal) {
        myDialog!!.setHandler(alertWindow.toLong())
      }

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
        Foundation.invoke(alertWindow, "setDefaultButtonCell:", Foundation.invoke(button, "cell"))
      }

      if (runModal) {
        setResult(alert, Foundation.invoke(alert, "runModal"))
      }
      else {
        Foundation.invoke(alert, "beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:", ownerWindow, self,
                          Foundation.createSelector("alertDidEnd:returnCode:contextInfo:"), ID.NIL)
      }
    }
  }

  private val ALERT_DID_END = object : Callback {
    @Suppress("UNUSED_PARAMETER", "unused")
    fun callback(self: ID, selector: String, alert: ID, returnCode: ID, contextInfo: ID) {
      setResult(alert, returnCode)
    }
  }

  private fun setResult(alert: ID, returnCode: ID) {
    myResult = returnCode.toInt()
    mySuppress = Foundation.invoke(Foundation.invoke(alert, "suppressionButton"), "state").toInt() == 1
    myDialog!!.close()
    Foundation.cfRelease(alert)
  }

  private val ALERT_CHANGE_JUST_MAIN = object : Callback {
    @Suppress("UNUSED_PARAMETER", "unused")
    fun callback(self: ID, selector: String) {
      myInfo!!.mainHandler()
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

    if (SystemInfo.isMacOSBigSur && !Foundation.addMethod(Foundation.getObjcClass("_NSAlertPanel"),
                                                          Foundation.createSelector("_changeJustMain"), ALERT_CHANGE_JUST_MAIN, "v")) {
      throw RuntimeException("Unable to add `_changeJustMain` method to Objective-C _NSAlertPanel class")
    }
  }
}

private fun getActualWindow(window: ID): ID? {
  if (!Foundation.invoke(window, "isVisible").booleanValue() || ID.NIL.equals(Foundation.invoke(window, "screen"))) {
    return null
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

private class MyDialog(parent: Window, private val runnable: () -> Unit) : JDialog(parent, ModalityType.APPLICATION_MODAL) {
  private var myPlatformWindow: Any? = null

  init {
    defaultCloseOperation = DISPOSE_ON_CLOSE
  }

  override fun addNotify() {
    try {
      val toolkit = Toolkit.getDefaultToolkit()
      val toolkitClass = Class.forName("sun.lwawt.LWToolkit")
      val pc = ReflectionUtil.getDeclaredMethod(toolkitClass, "createPlatformComponent")!!.invoke(toolkit)
      val pw = Class.forName("sun.lwawt.macosx.CPlatformLWWindow").getDeclaredConstructor().newInstance()
      val pwClass = Class.forName("sun.lwawt.PlatformWindow")
      val pwExt = Proxy.newProxyInstance(pwClass.classLoader, arrayOf(pwClass)) { _, method, args ->
        if ("setBounds" == method.name) {
          null
        }
        else if (args == null) {
          method.invoke(pw)
        }
        else method.invoke(pw, *args)
      }

      val enumClass = Class.forName("sun.lwawt.LWWindowPeer\$PeerType")
      val m = ReflectionUtil.getDeclaredMethod(toolkitClass, "createDelegatedPeer", Window::class.java,
                                               Class.forName("sun.lwawt.PlatformComponent"),
                                               Class.forName("sun.lwawt.PlatformWindow"), enumClass)
      val peer = m!!.invoke(toolkit, this, pc, pwExt, enumClass.enumConstants[2])

      ReflectionUtil.getDeclaredField(Component::class.java, "peer")!!.set(this, peer)

      ReflectionUtil.getDeclaredField(Class.forName("sun.lwawt.LWWindowPeer"), "platformWindow")!!.set(peer, pw)
      myPlatformWindow = pw

      super.addNotify()
      runnable()
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  override fun hide() {
    var blockedWindows: List<Window>? = null
    try {
      blockedWindows = ArrayList(ReflectionUtil.getDeclaredField(Dialog::class.java, "blockedWindows")!!.get(this) as List<Window>)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    super.hide()
    if (blockedWindows != null) {
      val owner = getOwner(this)
      for (window in blockedWindows) {
        if (owner === getOwner(window)) {
          window.toFront()
        }
      }
    }
  }

  private fun getOwner(window: Window): Window {
    return window.owner?.let { getOwner(it) } ?: window
  }

  fun setHandler(handler: Long) {
    try {
      ReflectionUtil.getDeclaredMethod(Class.forName("sun.lwawt.macosx.CFRetainedResource"), "setPtr",
                                       Long::class.javaPrimitiveType)!!.invoke(myPlatformWindow, handler)
      ReflectionUtil.getDeclaredField(myPlatformWindow!!.javaClass.getSuperclass(), "visible")!!.setBoolean(myPlatformWindow, handler != 0L)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  fun orderAboveSiblings() {
    try {
      ReflectionUtil.getDeclaredMethod(myPlatformWindow!!.javaClass.superclass, "windowDidBecomeMain")!!.invoke(myPlatformWindow)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  fun close() {
    setHandler(0)

    try {
      ReflectionUtil.getDeclaredMethod(Class.forName("sun.lwawt.LWToolkit"), "postEvent",
                                       AWTEvent::class.java)!!.invoke(null, WindowEvent(this, WindowEvent.WINDOW_CLOSING))
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}