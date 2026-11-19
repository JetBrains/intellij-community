// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import org.jetbrains.annotations.ApiStatus

/** Captures the audience, reports a show, and binds the query handler to the page. */
@ApiStatus.Internal
object WhatsNewAirPromoQueryHandler {
  @JvmStatic
  fun create(
    page: WhatsNewAirPromoPage,
    enabled: Boolean,
    delegate: HTMLEditorProvider.JsQueryHandler?,
  ): HTMLEditorProvider.JsQueryHandler? {
    if (enabled) {
      ApplicationManager.getApplication().messageBus.syncPublisher(WhatsNewAirPromoListener.TOPIC).blockShown(page)
    }
    return if (delegate is DefaultWhatsNewQueryHandler) delegate.forPage(page) else delegate
  }
}
