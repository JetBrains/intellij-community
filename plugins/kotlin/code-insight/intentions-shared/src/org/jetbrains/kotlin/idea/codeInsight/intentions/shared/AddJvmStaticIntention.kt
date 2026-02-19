// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.codeUsageScope
import org.jetbrains.kotlin.idea.base.util.restrictByFileType
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.getAddJvmStaticApplicabilityRange
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class AddJvmStaticIntention : SelfTargetingRangeIntention<KtNamedDeclaration>(
    KtNamedDeclaration::class.java,
    KotlinBundle.messagePointer("add.jvmstatic.annotation")
), LowPriorityAction {

    override fun startInWriteAction(): Boolean = false

    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? =
        getAddJvmStaticApplicabilityRange(element)

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val target = getTarget(editor, file) ?: return IntentionPreviewInfo.EMPTY
        target.addAnnotation(JvmStandardClassIds.Annotations.JvmStatic)
        return IntentionPreviewInfo.DIFF
    }

    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        val containingObject = element.containingClassOrObject as? KtObjectDeclaration ?: return
        val isCompanionMember = containingObject.isCompanion()
        val instanceFieldName = if (isCompanionMember) containingObject.name else JvmAbi.INSTANCE_FIELD
        val instanceFieldContainingClass = if (isCompanionMember) (containingObject.containingClassOrObject ?: return) else containingObject
        val project = element.project

        val expressionsToReplaceWithQualifier =
            project.runSynchronouslyWithProgress(KotlinBundle.message("looking.for.usages.in.java.files"), true) {
                runReadAction {
                    val searchScope = element.codeUsageScope().restrictByFileType(JavaFileType.INSTANCE)
                    ReferencesSearch
                        .search(element, searchScope)
                        .asIterable()
                        .mapNotNull {
                            val refExpr = it.element as? PsiReferenceExpression ?: return@mapNotNull null
                            if ((refExpr.resolve() as? KtLightElement<*, *>)?.kotlinOrigin != element) return@mapNotNull null
                            val qualifierExpr = refExpr.qualifierExpression as? PsiReferenceExpression ?: return@mapNotNull null
                            if (qualifierExpr.qualifierExpression == null) return@mapNotNull null
                            val instanceField = qualifierExpr.resolve() as? KtLightField ?: return@mapNotNull null
                            if (instanceField.name != instanceFieldName) return@mapNotNull null
                            if (instanceField.containingClass.kotlinOrigin != instanceFieldContainingClass) return@mapNotNull null
                            qualifierExpr
                        }
                }
            } ?: return

        runWriteAction {
            element.addAnnotation(JvmStandardClassIds.Annotations.JvmStatic)
            expressionsToReplaceWithQualifier.forEach { it.replace(it.qualifierExpression!!) }
        }
    }
}