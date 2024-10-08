// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.codeInsight.daemon.impl.TrafficLightRendererContributor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsProviderImpl
import org.jetbrains.kotlin.psi.KtFile

internal class ScriptTrafficLightRendererContributor : TrafficLightRendererContributor {
    @RequiresBackgroundThread
    override fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer? {
        val ktFile = (file as? KtFile)?.takeIf { runReadAction(it::isScript) } ?: return null
        return ScriptTrafficLightRenderer(ktFile.project, editor.document, ktFile)
    }

    class ScriptTrafficLightRenderer(project: Project, document: Document, private val file: KtFile) :
        TrafficLightRenderer(project, document) {
        override fun getDaemonCodeAnalyzerStatus(severityRegistrar: SeverityRegistrar): DaemonCodeAnalyzerStatus {
            val status = super.getDaemonCodeAnalyzerStatus(severityRegistrar)

            if (KotlinPluginModeProvider.isK2Mode()) {
                if (ScriptConfigurationsProviderImpl.getInstanceIfCreated(project)?.getScriptConfigurationResult(file) == null) {
                    status.reasonWhySuspended = KotlinBaseScriptingBundle.message("text.loading.kotlin.script.configuration")
                    status.errorAnalyzingFinished = false
                }
            } else {
                val configurations = ScriptConfigurationManager.getServiceIfCreated(project)
                if (configurations == null || configurations.isConfigurationLoadingInProgress(file)) {
                    status.reasonWhySuspended = KotlinBaseScriptingBundle.message("text.loading.kotlin.script.configuration")
                    status.errorAnalyzingFinished = false
                }
            }

            return status
        }
    }
}
