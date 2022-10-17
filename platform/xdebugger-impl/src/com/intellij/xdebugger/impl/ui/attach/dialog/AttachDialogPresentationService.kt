package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogItemPresentationProvider
import org.jetbrains.annotations.Nls

class AttachDialogPresentationService {
  private val providers = XAttachDialogItemPresentationProvider.EP.extensions.sortedBy { it.getPriority() }

  fun getItemPresentationInfo(item: AttachDialogProcessItem): AttachDialogItemPresentationInfo {
    val provider = providers.firstOrNull { it.isApplicableFor(item) } ?: return AttachDialogItemPresentationInfo(
      item.processInfo.executableDisplayName,
      null,
      item.processInfo.commandLine,
      null)

    return AttachDialogItemPresentationInfo(provider.getProcessExecutableText(item),
                                            provider.getProcessExecutableTextAttributes(item),
                                            provider.getProcessCommandLineText(item),
                                            provider.getProcessCommandLineTextAttributes(item))
  }
}

data class AttachDialogItemPresentationInfo(
  @Nls val executableText: String,
  val executableTextAttributes: SimpleTextAttributes?,
  @Nls val commandLineText: String,
  val commandLineTextAttributes: SimpleTextAttributes?
)