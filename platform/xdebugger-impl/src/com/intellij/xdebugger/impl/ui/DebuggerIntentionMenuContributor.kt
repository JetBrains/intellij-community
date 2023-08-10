// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.codeInsight.daemon.impl.IntentionMenuContributor
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass.IntentionsInfo
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class DebuggerIntentionMenuContributor : IntentionMenuContributor {
  override fun collectActions(hostEditor: Editor,
                              hostFile: PsiFile,
                              intentions: IntentionsInfo,
                              passIdToShowIntentionsFor: Int,
                              offset: Int) {
    if (Registry.`is`("debugger.inlayRunToCursor") && XDebuggerManager.getInstance(hostFile.project).currentSession != null) {
      intentions.topLevelActions.add(ActionManager.getInstance().getAction("RunToCursor"))
      ActionManager.getInstance().getAction("StreamTracerAction")?.let { intentions.topLevelActions.add(it) }
    }
  }
}
