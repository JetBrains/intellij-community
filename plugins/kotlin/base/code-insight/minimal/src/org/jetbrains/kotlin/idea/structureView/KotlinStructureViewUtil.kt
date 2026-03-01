// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.structureView

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCommonFile
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

fun isStructureViewNode(element: PsiElement?): Boolean =
    element is KtDeclaration &&
            element !is KtPropertyAccessor &&
            element !is KtFunctionLiteral &&
            !(element is KtParameter && !element.hasValOrVar()) &&
            !((element is KtProperty || element is KtFunction) && !element.topLevelDeclaration && element.containingClassOrObject !is KtNamedDeclaration)

private val KtDeclaration.topLevelDeclaration: Boolean
    get() = parent is PsiFile || parent is KtBlockExpression && parent.parent is KtScript

fun PsiElement.getStructureViewChildren(factory: (KtDeclaration) -> StructureViewTreeElement): Collection<StructureViewTreeElement> {
    val children = when (val element = this) {
        is KtCommonFile -> {
            val declarations = element.declarations
            if (element.isScript()) {
                (declarations.singleOrNull() as? KtScript) ?: element
            } else {
                element
            }.declarations
        }
        is KtClass -> element.getStructureDeclarations()
        is KtClassOrObject -> element.declarations
        is KtFunction, is KtClassInitializer, is KtProperty -> element.collectLocalDeclarations()
        else -> emptyList()
    }

    return children.map { factory(it) }
}

private fun PsiElement.collectLocalDeclarations(): List<KtDeclaration> {
    val result = mutableListOf<KtDeclaration>()

    acceptChildren(object : KtTreeVisitorVoid() {
        override fun visitClassOrObject(classOrObject: KtClassOrObject) {
            result.add(classOrObject)
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            result.add(function)
        }
    })

    return result
}

fun KtClassOrObject.getStructureDeclarations(): List<KtDeclaration> =
    buildList {
        primaryConstructor?.let { add(it) }
        primaryConstructorParameters.filterTo(this) { it.hasValOrVar() }
        addAll(declarations)
    }

