package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogItemPresentationProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogPresentationProvider
import org.jetbrains.annotations.Nls

class AttachDialogPresentationService {
  private val itemPresentationProviders = XAttachDialogItemPresentationProvider.EP.extensions.sortedBy { it.getPriority() }
  private val dialogPresentationProviders = XAttachDialogPresentationProvider.EP.extensions.sortedBy { it.getPriority() }

  fun getItemPresentationInfo(item: AttachDialogProcessItem): AttachDialogItemPresentationInfo {
    val provider = itemPresentationProviders.firstOrNull { it.isApplicableFor(item) } ?: throw IllegalStateException(
      "${AttachDialogDefaultItemPresentationProvider::class.java.simpleName} should always be available")

    return AttachDialogItemPresentationInfo(provider.getProcessExecutableText(item),
                                            provider.getProcessExecutableTextAttributes(item),
                                            provider.getProcessCommandLineText(item),
                                            provider.getProcessCommandLineTextAttributes(item),
                                            provider.getIndexedString(item))
  }

  @Nls
  @NlsContexts.Button
  fun getHostTypeDisplayText(hostType: AttachDialogHostType): String {
    val provider = dialogPresentationProviders.firstOrNull() ?: throw IllegalStateException(
      "${AttachDialogDefaultPresentationProvider::class.java.simpleName} should always be available")
    return provider.getCustomHostTypeDisplayText(hostType) ?: hostType.displayText
  }
}

data class AttachDialogItemPresentationInfo(
  @Nls val executableText: String,
  val executableTextAttributes: SimpleTextAttributes?,
  @Nls val commandLineText: String,
  val commandLineTextAttributes: SimpleTextAttributes?,
  val indexedString: String
)

class AttachDialogDefaultItemPresentationProvider : XAttachDialogItemPresentationProvider {
  override fun isApplicableFor(item: AttachDialogProcessItem): Boolean = true

  override fun getPriority(): Int = Int.MAX_VALUE
}

class AttachDialogDefaultPresentationProvider : XAttachDialogPresentationProvider {
  @Nls
  @NlsContexts.Button
  override fun getCustomHostTypeDisplayText(hostType: AttachDialogHostType): String? = null

  override fun getPriority(): Int = Int.MAX_VALUE
}