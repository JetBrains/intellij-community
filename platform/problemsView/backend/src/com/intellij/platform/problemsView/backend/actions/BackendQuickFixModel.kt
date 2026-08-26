// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend.actions

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.vfs.VirtualFile


internal data class IntentionOptionWithId(
  val action: IntentionAction,
  val intentionId: String,
  val text: String,
  val familyName: String
)

internal data class IntentionActionWithIds(
  val descriptor: HighlightInfo.IntentionActionDescriptor,
  val intentionId: String,
  val options: List<IntentionOptionWithId>,
  val text: String,
  val familyName: String
)

internal data class BackendQuickFixModel(
  val quickFixModelId: String,
  val file: VirtualFile? = null,
  val offset: Int = -1,
  val quickFixes: List<IntentionActionWithIds> = emptyList(),
) {

  fun findQuickFixById(intentionId: String): IntentionAction? {
    return quickFixes.firstNotNullOfOrNull { quickFix ->
      if (quickFix.intentionId == intentionId) {
        quickFix.descriptor.action
      }
      else {
        quickFix.options
          .firstOrNull { it.intentionId == intentionId }
          ?.action
      }
    }
  }
}
