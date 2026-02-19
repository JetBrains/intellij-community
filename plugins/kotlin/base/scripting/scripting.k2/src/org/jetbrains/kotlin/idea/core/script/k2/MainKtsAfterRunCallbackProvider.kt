// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.shared.ScriptAfterRunCallbackProvider
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.Path

class MainKtsAfterRunCallbackProvider(val project: Project, val scope: CoroutineScope) : ScriptAfterRunCallbackProvider {
    override fun provide(scriptPath: String): ProgramRunner.Callback? {
        val script = VfsUtil.findFile(Path( scriptPath), true) ?: return null
        if (!script.name.endsWith(".main.kts")) return null
        val ktFile = PsiManager.getInstance(project).findFile(script) as? KtFile ?: return null
        val action = ActionUtil.getAction("ReloadScriptConfiguration") ?: return null

        return ProgramRunner.Callback { _ ->
            val context = SimpleDataContext.builder()
                .add(CommonDataKeys.PSI_FILE, ktFile)
                .add(CommonDataKeys.PROJECT, project)
                .build()

            val event = AnActionEvent.createEvent(
                action, context, null, ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX, ActionUiKind.NONE, null
            )

            scope.launch {
                edtWriteAction {
                    ActionUtil.performAction(action, event)
                }
            }
        }
    }
}
