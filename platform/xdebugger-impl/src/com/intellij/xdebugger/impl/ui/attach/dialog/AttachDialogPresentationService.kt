// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogItemPresentationProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachDialogPresentationProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
@Service
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

@ApiStatus.Internal
data class AttachDialogItemPresentationInfo(
  @Nls val executableText: String,
  val executableTextAttributes: SimpleTextAttributes?,
  @Nls val commandLineText: String,
  val commandLineTextAttributes: SimpleTextAttributes?,
  val indexedString: String
)

@ApiStatus.Internal
class AttachDialogDefaultItemPresentationProvider : XAttachDialogItemPresentationProvider {
  override fun isApplicableFor(item: AttachDialogProcessItem): Boolean = true

  override fun getPriority(): Int = Int.MAX_VALUE
}

@ApiStatus.Internal
class AttachDialogDefaultPresentationProvider : XAttachDialogPresentationProvider {
  @Nls
  @NlsContexts.Button
  override fun getCustomHostTypeDisplayText(hostType: AttachDialogHostType): String? = null

  override fun getPriority(): Int = Int.MAX_VALUE
}