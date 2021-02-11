// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.ide.impl.TrustedProjectSettings
import com.intellij.ide.impl.getTrustedState
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ThreeState

sealed class TrustedModeAction(val state: ThreeState) : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    if (state == e.project?.getTrustedState()) return
    e.project?.service<TrustedProjectSettings>()?.trustedState = state
  }

  override fun update(e: AnActionEvent) {
    if (state == e.project?.getTrustedState()) {
      e.presentation.isEnabled = false
    }
    e.presentation.isVisible = true
  }
}

class UnsureTrustAction : TrustedModeAction(ThreeState.UNSURE)
class YesTrustAction : TrustedModeAction(ThreeState.YES)
class NoTrustAction : TrustedModeAction(ThreeState.NO)

