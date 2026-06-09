// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.kdoc

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.KDocUnresolvedLinkQuickFixFactory
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * This is a paired annotator for [KDocUnresolvedReferenceInspection].
 *
 * The annotator finds unresolved KDoc links and creates "Add qualifier" quick-fixes for them.
 */
class KDocUnresolvedLinkAnnotator : ExternalAnnotator<List<KDocReference>, List<KDocReference>>() {
    override fun apply(psiFile: PsiFile, annotationResult: List<KDocReference>, holder: AnnotationHolder) {
        if (annotationResult.isEmpty()) {
            return
        }

        val displayLevel = getHighlightDisplayLevel(psiFile) ?: return

        annotationResult.forEach { reference ->
            val message = KotlinBundle.message("inspection.k.doc.unresolved.link.message", reference.canonicalText)
            val builder = holder.newAnnotation(displayLevel.severity, message).range(reference.absoluteRange)

            createQuickFix(reference.element)?.let {
                builder.withFix(it)
            }

            builder.create()
        }
    }

    override fun doAnnotate(collectedInfo: List<KDocReference>): List<KDocReference> {
        return collectedInfo
    }

    override fun collectInformation(psiFile: PsiFile, editor: Editor, hasErrors: Boolean): List<KDocReference> {
        return collectInformation(psiFile)
    }

    override fun collectInformation(psiFile: PsiFile): List<KDocReference> {
        if (psiFile !is KtFile) {
            return emptyList()
        }

        val unresolvedReferences = mutableListOf<KDocReference>()

        psiFile.accept(object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                if (element is KDocName) {
                    val reference = element.mainReference
                    if (reference.multiResolve(incompleteCode = false).isEmpty()) {

                        val containingKDocLink = element.getStrictParentOfType<KDocLink>()
                        val isSubjectOfSampleTag = containingKDocLink?.getTagIfSubject()?.knownTag == KDocKnownTag.SAMPLE

                        // Some `@sample` tag subjects are not resolved at the moment due to the implementation limitations
                        // and lack of design. To avoid polluting the inspection results, we have introduced a registry key
                        // as a temporary workaround. See KTIJ-21248, OSIP-678 and KTIJ-8414
                        if (!isSubjectOfSampleTag || RegistryManager.getInstance().`is`("kotlin.kdoc.should.report.samples")) {
                            unresolvedReferences.add(reference)
                        }
                    }
                }
                element.acceptChildren(this)
            }
        })

        return unresolvedReferences
    }

    override fun getPairedBatchInspectionShortName(): String = KDocUnresolvedReferenceInspection.SHORT_NAME

    private fun getHighlightDisplayLevel(context: PsiElement): HighlightDisplayLevel? {
        val inspectionProfile =
            InspectionProjectProfileManager.getInstance(context.project).getCurrentProfile()
        val displayKey = HighlightDisplayKey.find(KDocUnresolvedReferenceInspection.SHORT_NAME) ?: return null
        return inspectionProfile.getErrorLevel(displayKey, context)
    }

    private fun createQuickFix(kDocName: KDocName): IntentionAction? =
        KDocUnresolvedLinkQuickFixFactory.getInstance()?.createQuickFix(kDocName)
}
