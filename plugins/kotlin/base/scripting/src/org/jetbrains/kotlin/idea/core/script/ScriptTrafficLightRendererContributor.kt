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
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class ScriptTrafficLightRendererContributor : TrafficLightRendererContributor {
    @RequiresBackgroundThread
    override fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer? {
        val ktFile = file.safeAs<KtFile>() ?: return null
        val isScript = runReadAction { ktFile.isScript() /* RequiresBackgroundThread */}
        return if (isScript) {
            ScriptTrafficLightRenderer(ktFile.project, editor.document, ktFile)
        } else {
            null
        }
    }

    class ScriptTrafficLightRenderer(project: Project, document: Document, private val file: KtFile) :
        TrafficLightRenderer(project, document) {
        override fun getDaemonCodeAnalyzerStatus(severityRegistrar: SeverityRegistrar): DaemonCodeAnalyzerStatus {
            val status = super.getDaemonCodeAnalyzerStatus(severityRegistrar)

            val configurations = ScriptConfigurationManager.getServiceIfCreated(project)
            if (configurations == null) {
                // services not yet initialized (it should be initialized under the LoadScriptDefinitionsStartupActivity)
                status.reasonWhySuspended = KotlinBaseScriptingBundle.message("text.loading.kotlin.script.configuration")
                status.errorAnalyzingFinished = false
            } else if (configurations.isConfigurationLoadingInProgress(file)) {
                status.reasonWhySuspended = KotlinBaseScriptingBundle.message("text.loading.kotlin.script.configuration")
                status.errorAnalyzingFinished = false
            }
            return status
        }
    }
}
