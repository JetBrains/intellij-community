// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting

import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.plugins.gradle.service.resolve.Coordinates
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogPsiResolverUtil

class GradleVersionCatalogVersionInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        if (!file.name.endsWith(".gradle.kts")) return null
        return GradleVersionCatalogVersionInlayHintsCollector()
    }
}

private val log: Logger = Logger.getInstance(GradleVersionCatalogVersionInlayHintsCollector::class.java)

private class GradleVersionCatalogVersionInlayHintsCollector : SharedBypassCollector {
    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
        if (element !is KtDotQualifiedExpression || element.parent is KtDotQualifiedExpression) return

        val coordinates = element.resolveVersionCatalogCoordinates() ?: return
        log.debug("coordsFinal=$coordinates")

        val line =
            PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile)?.getLineNumber(element.textRange.endOffset)
                ?: return

        sink.addPresentation(
            EndOfLinePosition(line),
            hintFormat = HintFormat.default,
        ) {
            text(coordinates.presentableString)
        }
    }
}

private fun KtDotQualifiedExpression.resolveVersionCatalogCoordinates(): Coordinates? {
    val segments = collectChainSegments()
    if (segments.size < 2) return null
    val catalogName = segments[0]
    val entryPath = segments.drop(1).joinToString(".")
    return GradleVersionCatalogPsiResolverUtil.getResolvedCoordinatesByPath(catalogName, entryPath, this)
}

/**
 * Collects all identifier segments from a dot-qualified expression chain.
 * For example, `libs.my.lib` returns `["libs", "my", "lib"]`.
 */
private fun KtDotQualifiedExpression.collectChainSegments(): List<String> {
    val segments = mutableListOf<String>()
    var current: KtExpression = this
    while (current is KtDotQualifiedExpression) {
        val selectorName = (current.selectorExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return emptyList()
        segments.add(0, selectorName)
        current = current.receiverExpression
    }
    val receiverName = (current as? KtNameReferenceExpression)?.getReferencedName() ?: return emptyList()
    segments.add(0, receiverName)
    return segments
}
