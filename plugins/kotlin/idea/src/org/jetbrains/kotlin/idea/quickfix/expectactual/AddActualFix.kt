// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.expectactual

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.TypeAccessibilityChecker
import org.jetbrains.kotlin.idea.refactoring.getExpressionShortText
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddActualFix(
    actualClassOrObject: KtClassOrObject,
    missedDeclarations: List<KtDeclaration>
) : KotlinQuickFixAction<KtClassOrObject>(actualClassOrObject) {
    private val missedDeclarationPointers = missedDeclarations.map { it.createSmartPointer() }

    override fun getFamilyName() = text

    override fun getText() = KotlinBundle.message("fix.create.missing.actual.members")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val psiFactory = KtPsiFactory(project)

        val codeStyleManager = CodeStyleManager.getInstance(project)

        fun PsiElement.clean() {
            ShortenReferences.DEFAULT.process(codeStyleManager.reformat(this) as KtElement)
        }

        val module = element.module ?: return
        val checker = TypeAccessibilityChecker.create(project, module)
        val errors = linkedMapOf<KtDeclaration, KotlinTypeInaccessibleException>()
        for (missedDeclaration in missedDeclarationPointers.mapNotNull { it.element }) {
            val actualDeclaration = try {
                when (missedDeclaration) {
                    is KtClassOrObject -> psiFactory.generateClassOrObject(project, false, missedDeclaration, checker)
                    is KtFunction, is KtProperty -> missedDeclaration.toDescriptor()?.safeAs<CallableMemberDescriptor>()?.let {
                        generateCallable(project, false, missedDeclaration, it, element, checker = checker)
                    }
                    else -> null
                } ?: continue
            } catch (e: KotlinTypeInaccessibleException) {
                errors += missedDeclaration to e
                continue
            }

            if (actualDeclaration is KtPrimaryConstructor) {
                if (element.primaryConstructor == null)
                    element.addAfter(actualDeclaration, element.nameIdentifier).clean()
            } else {
                element.addDeclaration(actualDeclaration).clean()
            }
        }

        if (errors.isNotEmpty()) {
            val message = errors.entries.joinToString(
                separator = "\n",
                prefix = KotlinBundle.message("fix.create.declaration.error.some.types.inaccessible") + "\n"
            ) { (declaration, error) ->
                getExpressionShortText(declaration) + " -> " + error.message
            }
            showInaccessibleDeclarationError(element, message, editor)
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val missedDeclarations = DiagnosticFactory.cast(diagnostic, Errors.NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS).b.mapNotNull {
                DescriptorToSourceUtils.descriptorToDeclaration(it.first) as? KtDeclaration
            }.ifEmpty { return null }

            return (diagnostic.psiElement as? KtClassOrObject)?.let {
                AddActualFix(it, missedDeclarations)
            }
        }
    }
}