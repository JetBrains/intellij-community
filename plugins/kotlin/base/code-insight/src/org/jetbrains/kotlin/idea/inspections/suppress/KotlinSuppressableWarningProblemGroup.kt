// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.suppress

import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinBaseCodeInsightBundle
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal const val PRIORITY_STATEMENT = 10
internal const val PRIORITY_PARAMETER = 30
internal const val PRIORITY_MEMBER = 40
internal const val PRIORITY_ENUM_ENTRY = 45
internal const val PRIORITY_CLASS = 50
internal const val PRIORITY_FILE = 60

class KotlinSuppressableWarningProblemGroup(private val factoryName: String) : SuppressableProblemGroup {
    override fun getProblemName(): String = factoryName

    override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction> {
        return if (element != null) {
            createSuppressWarningActions(element, Severity.WARNING, factoryName).toTypedArray()
        } else {
            SuppressIntentionAction.EMPTY_ARRAY
        }
    }
}

fun createSuppressWarningActions(element: PsiElement, severity: Severity, suppressionId: String): List<KotlinSuppressIntentionAction> {
    if (severity != Severity.WARNING) {
        return emptyList()
    }

    val suppressionKey = calculateSuppressionKey(element, suppressionId)

    val actions = arrayListOf<KotlinSuppressIntentionAction>()
    var current: PsiElement? = element
    var suppressAtStatementAllowed = true
    while (current != null) {
        when (current) {
            is KtDeclaration if current !is KtDestructuringDeclaration -> {
                val declaration = current
                val kind = DeclarationKindDetector.detect(declaration)
                if (kind != null) {
                    actions.add(KotlinSuppressIntentionAction(declaration, suppressionKey, kind))
                }
                suppressAtStatementAllowed = false
            }

            is KtExpression if suppressAtStatementAllowed -> {
                val hostKind = when {
                    current.parent is KtBlockExpression -> AnnotationHostKind(
                        KotlinBaseCodeInsightBundle.message("declaration.kind.statement"),
                        null,
                        true,
                        PRIORITY_STATEMENT
                    )

                    current.parent is KtDestructuringDeclaration -> AnnotationHostKind(
                        KotlinBaseCodeInsightBundle.message("declaration.kind.initializer"),
                        null,
                        true,
                        PRIORITY_STATEMENT
                    )

                    current.parent is KtNamedFunction && (current.parent as KtNamedFunction).bodyExpression == current -> AnnotationHostKind(
                        KotlinBaseCodeInsightBundle.message("declaration.kind.statement"),
                        null,
                        true,
                        PRIORITY_STATEMENT
                    )

                    else -> null
                }
                if (hostKind != null) {
                    actions.add(KotlinSuppressIntentionAction(current, suppressionKey, hostKind))
                    suppressAtStatementAllowed = false
                }
            }

            is PsiWhiteSpace if current.prevSibling is KtClassLikeDeclaration -> {
                current = current.prevSibling
                continue
            }

            is KtFile -> {
                val hostKind =
                    AnnotationHostKind(KotlinBaseCodeInsightBundle.message("declaration.kind.file"), current.name, true, PRIORITY_FILE)
                actions.add(KotlinSuppressIntentionAction(current, suppressionKey, hostKind))
                break
            }
        }

        current = current.parent
    }

    return actions
}

private fun calculateSuppressionKey(element: PsiElement, suppressionId: String): String {
    return if (element.parent is KtParameter && suppressionId == "unused") {
        "UNUSED_PARAMETER"
    } else {
        suppressionId
    }
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
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true, priority = PRIORITY_CLASS)
    }

    override fun visitNamedFunction(declaration: KtNamedFunction, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.fun")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true, priority = PRIORITY_MEMBER)
    }

    override fun visitProperty(declaration: KtProperty, data: Unit?): AnnotationHostKind {
        val kind = when {
            declaration.isVar -> KotlinBaseCodeInsightBundle.message("declaration.kind.var")
            else -> KotlinBaseCodeInsightBundle.message("declaration.kind.val")
        }
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true, priority = PRIORITY_MEMBER)
    }

    override fun visitTypeParameter(declaration: KtTypeParameter, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.type.parameter")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = false, priority = PRIORITY_PARAMETER)
    }

    override fun visitEnumEntry(declaration: KtEnumEntry, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.enum.entry")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true, priority = PRIORITY_ENUM_ENTRY)
    }

    override fun visitParameter(declaration: KtParameter, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.parameter")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = false, priority = PRIORITY_PARAMETER)
    }

    override fun visitSecondaryConstructor(declaration: KtSecondaryConstructor, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.secondary.constructor.of")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true, priority = PRIORITY_MEMBER)
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
                AnnotationHostKind(kind, name, newLineNeeded = true, priority = PRIORITY_CLASS)
            }

            d.parent is KtObjectLiteralExpression -> null
            else -> {
                val kind = KotlinBaseCodeInsightBundle.message("declaration.kind.object")
                AnnotationHostKind(kind, getDeclarationName(d), newLineNeeded = true, priority = PRIORITY_CLASS)
            }
        }
    }
}