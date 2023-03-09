// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.highlighting.highlighters.AfterResolveHighlighter
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * This highlighting pass executes highlighters located in "highlighters/" package as a separate [TextEditorHighlightingPass].
 * These are not executed inside the [GeneralHighlightingPass] so the function/type/variable reference highlighting can be done
 * separately from the compiler diagnostics. The compiler diagnostics are collected in the [KotlinDiagnosticHighlightVisitor].
 */
class KotlinSemanticHighlightingPass(
    private val ktFile: KtFile,
    document: Document,
) : TextEditorHighlightingPass(ktFile.project, document) {

    private val annotationHolder = AnnotationHolderImpl(AnnotationSession(ktFile), false)

    override fun doCollectInformation(progress: ProgressIndicator) {
        if (isDispatchThread()) {
            throw ProcessCanceledException()
        }
        val highlighters = AfterResolveHighlighter.createHighlighters(annotationHolder, ktFile.project)
        ktFile.descendantsOfType<KtElement>().forEach { element ->
            analyze(element) {
                highlighters.forEach { highlighter ->
                    annotationHolder.runAnnotatorWithContext(element) { _, _ ->
                        highlighter.highlight(element)
                    }
                }
            }
        }
    }

    override fun doApplyInformationToEditor() {
        val diagnosticInfos = annotationHolder.map(HighlightInfo::fromAnnotation)
        UpdateHighlightersUtil.setHighlightersToEditor(
            myProject, myDocument, /* startOffset = */ 0, ktFile.textLength, diagnosticInfos, colorsScheme, id
        )
    }
}

internal class KotlinSemanticHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
    }

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (file !is KtFile) return null
        if (file.isCompiled) return null
        return KotlinSemanticHighlightingPass(file, editor.document)
    }
}
