package com.intellij.xdebugger.impl.ui.attach.dialog.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
interface XAttachDialogItemPresentationProvider {

  companion object {
    val EP: ExtensionPointName<XAttachDialogItemPresentationProvider> = ExtensionPointName.create("com.intellij.xdebugger.dialog.presentation.provider")
  }

  fun isApplicableFor(item: AttachDialogProcessItem): Boolean
  @Nls fun getProcessExecutableText(item: AttachDialogProcessItem): String {
    return item.processInfo.executableDisplayName
  }
  @Nls fun getProcessCommandLineText(item: AttachDialogProcessItem): String {
    return item.processInfo.commandLine
  }

  fun getProcessExecutableTextAttributes(item: AttachDialogProcessItem): SimpleTextAttributes? {
    return null
  }
  fun getProcessCommandLineTextAttributes(item: AttachDialogProcessItem): SimpleTextAttributes? {
    return null
  }

  fun getIndexedString(item: AttachDialogProcessItem): String {
    return "${item.processInfo.pid} ${getProcessExecutableText(item)}"
  }

  fun getPriority(): Int
}