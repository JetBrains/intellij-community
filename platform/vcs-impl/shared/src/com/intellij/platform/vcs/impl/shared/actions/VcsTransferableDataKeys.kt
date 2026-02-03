// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.actions

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSelection
import org.jetbrains.annotations.ApiStatus

/**
 * Data keys that are used for propagating data context from frontend to backend.
 * Note that values are not transferred automatically and either [CustomDataContextSerializer] should be registered.
 *
 * [UiDataRule] can be used to restore actual data keys.
 */
@ApiStatus.Internal
object VcsTransferableDataKeys {
  @JvmField
  val CHANGES_VIEW_SELECTION: DataKey<ChangesViewSelection> = DataKey.create("VcsTransferableDataKeys.CHANGES_VIEW_SELECTION")
}

internal class ChangesViewSelectionDataContextSerializer: CustomDataContextSerializer<ChangesViewSelection> {
  override val key = VcsTransferableDataKeys.CHANGES_VIEW_SELECTION
  override val serializer = ChangesViewSelection.serializer()
}