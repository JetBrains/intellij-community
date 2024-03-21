package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Remote
import com.intellij.openapi.util.SystemInfo
import java.awt.event.KeyEvent

fun UiRobot.pasteText(text: String) {
  driver.utility(ToolkitRef::class)
    .getDefaultToolkit()
    .getSystemClipboard()
    .setContents(driver.new(StringSelectionRef::class, text), null)
  keyboard {
    val keyEvent = if (SystemInfo.isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL
    hotKey(keyEvent, KeyEvent.VK_V)
  }
}

@Remote("java.awt.Toolkit")
interface ToolkitRef {
  fun getDefaultToolkit(): ToolkitRef
  fun getSystemClipboard(): ClipboardRef
}

@Remote("java.awt.datatransfer.Clipboard")
interface ClipboardRef {
  fun setContents(content: StringSelectionRef, ownerRef: ClipboardOwnerRef?)
}

@Remote("java.awt.datatransfer.ClipboardOwner")
interface ClipboardOwnerRef

@Remote("java.awt.datatransfer.StringSelection")
interface StringSelectionRef