// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.fe10.highlighting.KotlinBaseFe10HighlightingBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class KotlinSuppressableWarningProblemGroup(private val diagnosticFactory: DiagnosticFactory<*>) : SuppressableProblemGroup {
    init {
        assert(diagnosticFactory.severity == Severity.WARNING)
    }

    override fun getProblemName() = diagnosticFactory.name

    override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction> {
        return if (element != null) {
            createSuppressWarningActions(element, diagnosticFactory).toTypedArray()
        } else {
            SuppressIntentionAction.EMPTY_ARRAY
        }
    }
}

fun createSuppressWarningActions(element: PsiElement, diagnosticFactory: DiagnosticFactory<*>): List<SuppressIntentionAction> =
    createSuppressWarningActions(element, diagnosticFactory.severity, diagnosticFactory.name)

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
                if (kind != null) {
                    actions.add(Fe10QuickFixProvider.getInstance(declaration.project).createSuppressFix(declaration, suppressionKey, kind))
                }
                suppressAtStatementAllowed = false
            }

            current is KtExpression && suppressAtStatementAllowed -> {
                // Add suppress action at first statement
                if (current.parent is KtBlockExpression || current.parent is KtDestructuringDeclaration) {
                    val kind = if (current.parent is KtBlockExpression)
                        KotlinBaseFe10HighlightingBundle.message("declaration.kind.statement")
                    else
                        KotlinBaseFe10HighlightingBundle.message("declaration.kind.initializer")

                    val hostKind = AnnotationHostKind(kind, null, true)
                    actions.add(Fe10QuickFixProvider.getInstance(current.project).createSuppressFix(current, suppressionKey, hostKind))
                    suppressAtStatementAllowed = false
                }
            }

            current is KtFile -> {
                val hostKind = AnnotationHostKind(KotlinBaseFe10HighlightingBundle.message("declaration.kind.file"), current.name, true)
                actions.add(Fe10QuickFixProvider.getInstance(current.project).createSuppressFix(current, suppressionKey, hostKind))
                suppressAtStatementAllowed = false
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
        return declaration.name ?: KotlinBaseFe10HighlightingBundle.message("declaration.name.anonymous")
    }

    override fun visitClass(declaration: KtClass, data: Unit?): AnnotationHostKind {
        val kind = when {
            declaration.isInterface() -> KotlinBaseFe10HighlightingBundle.message("declaration.kind.interface")
            else -> KotlinBaseFe10HighlightingBundle.message("declaration.kind.class")
        }
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitNamedFunction(declaration: KtNamedFunction, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseFe10HighlightingBundle.message("declaration.kind.fun")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitProperty(declaration: KtProperty, data: Unit?): AnnotationHostKind {
        val kind = when {
            declaration.isVar -> KotlinBaseFe10HighlightingBundle.message("declaration.kind.var")
            else -> KotlinBaseFe10HighlightingBundle.message("declaration.kind.val")
        }
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitDestructuringDeclaration(declaration: KtDestructuringDeclaration, data: Unit?): AnnotationHostKind {
        val kind = when {
            declaration.isVar -> KotlinBaseFe10HighlightingBundle.message("declaration.kind.var")
            else -> KotlinBaseFe10HighlightingBundle.message("declaration.kind.val")
        }
        val name = declaration.entries.joinToString(", ", "(", ")") { it.name!! }
        return AnnotationHostKind(kind, name, newLineNeeded = true)
    }

    override fun visitTypeParameter(declaration: KtTypeParameter, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseFe10HighlightingBundle.message("declaration.kind.type.parameter")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = false)
    }

    override fun visitEnumEntry(declaration: KtEnumEntry, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseFe10HighlightingBundle.message("declaration.kind.enum.entry")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitParameter(declaration: KtParameter, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseFe10HighlightingBundle.message("declaration.kind.parameter")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = false)
    }

    override fun visitSecondaryConstructor(declaration: KtSecondaryConstructor, data: Unit?): AnnotationHostKind {
        val kind = KotlinBaseFe10HighlightingBundle.message("declaration.kind.secondary.constructor.of")
        return AnnotationHostKind(kind, getDeclarationName(declaration), newLineNeeded = true)
    }

    override fun visitObjectDeclaration(d: KtObjectDeclaration, data: Unit?): AnnotationHostKind? {
        return when {
            d.isCompanion() -> {
                val kind = KotlinBaseFe10HighlightingBundle.message("declaration.kind.companion.object")
                val name = KotlinBaseFe10HighlightingBundle.message(
                    "declaration.name.0.of.1",
                    d.name.toString(),
                    d.getStrictParentOfType<KtClass>()?.name.toString()
                )
                AnnotationHostKind(kind, name, newLineNeeded = true)
            }
            d.parent is KtObjectLiteralExpression -> null
            else -> {
                val kind = KotlinBaseFe10HighlightingBundle.message("declaration.kind.object")
                AnnotationHostKind(kind, getDeclarationName(d), newLineNeeded = true)
            }
        }
    }
}