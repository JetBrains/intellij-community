// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.split

import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.UID
import com.intellij.ui.RemoteTransferUIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

@Serializable
@ApiStatus.Internal
data class SplitComponentId(val placeId: String, val modelUid: UID) {
  override fun toString(): String {
    return "$modelUid/$placeId"
  }
}

@ApiStatus.Internal
class SplitComponentPlaceholder(
  val project: Project,
  val scope: CoroutineScope,
  val id: SplitComponentId,
) : JPanel() {
  init {
    RemoteTransferUIManager.setWellBeControlizableAndPaintedQuickly(this)
  }
}