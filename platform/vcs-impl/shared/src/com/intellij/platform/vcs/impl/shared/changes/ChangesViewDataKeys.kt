// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.util.Key
import com.intellij.ui.content.Content
import com.intellij.util.IJSwingUtilities
import org.jetbrains.annotations.ApiStatus

private typealias ContentSupplier = (Content) -> Unit

@ApiStatus.Internal
object ChangesViewDataKeys {
  @JvmField
  val CONTENT_SUPPLIER: Key<ContentSupplier?> = Key.create("CONTENT_SUPPLIER")

  fun initLazyContent(content: Content) {
    val provider = content.getUserData(CONTENT_SUPPLIER) ?: return
    content.putUserData(CONTENT_SUPPLIER, null)
    provider.invoke(content)
    IJSwingUtilities.updateComponentTreeUI(content.component)
  }
}