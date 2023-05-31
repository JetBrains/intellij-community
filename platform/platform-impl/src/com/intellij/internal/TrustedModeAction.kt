// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.impl.isTrusted
import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal sealed class TrustedModeAction(val targetState: Boolean) : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.setTrusted(targetState)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = (targetState != e.project?.isTrusted())
  }
}

internal class YesTrustAction : TrustedModeAction(true)
internal class NoTrustAction : TrustedModeAction(false)

