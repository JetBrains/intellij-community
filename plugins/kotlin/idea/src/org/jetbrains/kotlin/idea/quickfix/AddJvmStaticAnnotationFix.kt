// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.annotations.JVM_STATIC_ANNOTATION_FQ_NAME

class AddJvmStaticAnnotationFix(declaration: KtCallableDeclaration) : AddAnnotationFix(
    declaration,
    ClassId.topLevel(JVM_STATIC_ANNOTATION_FQ_NAME),
    Kind.Declaration(declaration.nameAsSafeName.asString())
) {
    override fun getFamilyName(): String = text

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val nameReference = diagnostic.psiElement as? KtNameReferenceExpression ?: return null
            val resolved = nameReference.mainReference.resolve() ?: return null
            return if (resolved is KtProperty || resolved is KtNamedFunction) {
                AddJvmStaticAnnotationFix(resolved as KtCallableDeclaration)
            } else {
                null
            }
        }
    }
}
