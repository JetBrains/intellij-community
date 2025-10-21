// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.codeInsight.daemon.impl.TrafficLightRendererContributor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider

internal class ScriptTrafficLightRendererContributor : TrafficLightRendererContributor {
    @RequiresBackgroundThread
    override fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer? {
        val ktFile = (file as? KtFile)?.takeIf { runReadAction(it::isScript) } ?: return null
        return ScriptTrafficLightRenderer(ktFile.project, editor, ktFile)
    }

    class ScriptTrafficLightRenderer(project: Project, editor: Editor, private val file: KtFile) :
      TrafficLightRenderer(project, editor) {
        override fun getDaemonCodeAnalyzerStatus(severityRegistrar: SeverityRegistrar): DaemonCodeAnalyzerStatus {
            val status = super.getDaemonCodeAnalyzerStatus(severityRegistrar)

            if (ScriptConfigurationsProvider.getInstance(project)?.getScriptConfigurationResult(file) == null) {
                status.reasonWhySuspended = KotlinBaseScriptingBundle.message("text.loading.kotlin.script.configuration")
                status.errorAnalyzingFinished = false
            }

            return status
        }
    }
}