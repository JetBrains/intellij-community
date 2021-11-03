// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.ide.impl.isTrusted
import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

sealed class TrustedModeAction(val targetState: Boolean) : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.setTrusted(targetState)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = (targetState != e.project?.isTrusted())
  }
}

class YesTrustAction : TrustedModeAction(true)
class NoTrustAction : TrustedModeAction(false)

