package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogItemPresentationProvider
import org.jetbrains.annotations.Nls

class AttachDialogPresentationService {
  private val providers = XAttachDialogItemPresentationProvider.EP.extensions.sortedBy { it.getPriority() }

  fun getItemPresentationInfo(item: AttachDialogProcessItem): AttachDialogItemPresentationInfo {
    val provider = providers.firstOrNull { it.isApplicableFor(item) } ?:
    throw IllegalStateException("${AttachDialogDefaultItemPresentationProvider::class.java.simpleName} should always be available")

    return AttachDialogItemPresentationInfo(provider.getProcessExecutableText(item),
                                            provider.getProcessExecutableTextAttributes(item),
                                            provider.getProcessCommandLineText(item),
                                            provider.getProcessCommandLineTextAttributes(item),
                                            provider.getIndexedString(item))
  }
}

data class AttachDialogItemPresentationInfo(
  @Nls val executableText: String,
  val executableTextAttributes: SimpleTextAttributes?,
  @Nls val commandLineText: String,
  val commandLineTextAttributes: SimpleTextAttributes?,
  val indexedString: String
)

class AttachDialogDefaultItemPresentationProvider: XAttachDialogItemPresentationProvider {
  override fun isApplicableFor(item: AttachDialogProcessItem): Boolean = true

  override fun getPriority(): Int = Int.MAX_VALUE
}