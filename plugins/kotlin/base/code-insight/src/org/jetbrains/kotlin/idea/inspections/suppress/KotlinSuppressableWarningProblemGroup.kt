// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.suppress

import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinBaseCodeInsightBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class KotlinSuppressableWarningProblemGroup(private val factoryName: String) : SuppressableProblemGroup {
    override fun getProblemName() = factoryName

    override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction> {
        return if (element != null) {
            createSuppressWarningActions(element, Severity.WARNING, factoryName).toTypedArray()
        } else {
            SuppressIntentionAction.EMPTY_ARRAY
        }
    }
}

fun createSuppressWarningActions(element: PsiElement, severity: Severity, suppressionKey: String): List<SuppressIntentionAction> {
    if (severity != Severity.WARNING) {
        return emptyList()
    }

    val actions = arrayListOf<SuppressIntentionAction>()
    var current: PsiElement? = element
    var suppressAtStatementAllowed = true
    while (current != null) {
        when {
            current is KtDeclaration && current !is KtDestructuringDeclaration -> {
                val declaration = current
                val kind = DeclarationKindDetector.detect(declaration)
                if (kind != null ) {
                    actions.add(KotlinSuppressIntentionAction(declaration, suppressionKey, kind))
                }
                suppressAtStatementAllowed = false
            }

            current is KtExpression && suppressAtStatementAllowed -> {
                // Add suppress action at first statement
                if (current.parent is KtBlockExpression || current.parent is KtDestructuringDeclaration) {
                    val kind = if (current.parent is KtBlockExpression)
                        KotlinBaseCodeInsightBundle.message("declaration.kind.statement")
                    else
                        KotlinBaseCodeInsightBundle.message("declaration.kind.initializer")

                    val hostKind = AnnotationHostKind(kind, null, true)
                    actions.add(KotlinSuppressIntentionAction(current, suppressionKey, hostKind))
                    suppressAtStatementAllowed = false
                }
            }

            current is PsiWhiteSpace && current.prevSibling is KtClassLikeDeclaration -> {
                current = current.prevSibling
                continue
            }

            current is KtFile -> {
                val hostKind = AnnotationHostKind(KotlinBaseCodeInsightBundle.message("declaration.kind.file"), current.name, true)
                actions.add(KotlinSuppressIntentionAction(current, suppressionKey, hostKind))
                break
            }
        }

        current = current.parent
    }

    return actions
}

private object DeclarationKindDetector : KtVisitor<AnnotationHostKind?, Unit?>() {
    fun detect(declaration: KtDeclaration) = declaration.accept(this, null)

    override fun visitDeclaration(declaration: KtDeclaration, data: Unit?) = null

    private fun getDeclarationName(declaration: KtDeclaration): @NlsSafe String {
        return declaration.name ?: KotlinBaseCodeInsightBundle.message("declaration.name.anonymous")
    }

    override fun visitClass(declaration: KtClass, data: Unit?): AnnotationHostKind {
        val kind = when {
            declaration.isInterface() -> KotlinBaseCodeInsightBundle.message("declaration.kind.interface")
            else -> KotlinBaseCodeInsightBundle.message("declaration.kind.class")
        }
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitNamedFunction(declaration: KtNamedFunction, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.fun")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitProperty(declaration: KtProperty, data: Unit?): AnnotationHostKind {
        val kind = when {
            declaration.isVar -> KotlinBaseCodeInsightBundle.message("declaration.kind.var")
            else -> KotlinBaseCodeInsightBundle.message("declaration.kind.val")
        }
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitTypeParameter(declaration: KtTypeParameter, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.type.parameter")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = false)
    }

    override fun visitEnumEntry(declaration: KtEnumEntry, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.enum.entry")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitParameter(declaration: KtParameter, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.parameter")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = false)
    }

    override fun visitSecondaryConstructor(declaration: KtSecondaryConstructor, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.secondary.constructor.of")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitObjectDeclaration(d: KtObjectDeclaration, data: Unit?): AnnotationHostKind? {
        return when {
            d.isCompanion() -> {
                val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.companion.object")
                val name = KotlinBaseCodeInsightBundle.message(
                    "declaration.name.0.of.1",
                    d.name.toString(),
                    d.getStrictParentOfType<KtClass>()?.name.toString()
                )
                AnnotationHostKind(kind, name, newLineNeeded = true)
            }
            d.parent is KtObjectLiteralExpression -> null
            else -> {
                val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.object")
                AnnotationHostKind(kind, getDeclarationName(d), newLineNeeded = true)
            }
        }
    }
}