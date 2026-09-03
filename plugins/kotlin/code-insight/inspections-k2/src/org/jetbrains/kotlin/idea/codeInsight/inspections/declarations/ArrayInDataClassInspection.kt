// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.declarations

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.isArrayOrPrimitiveArray
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.addMemberDeclaration
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinEqualsHashCodeToStringSymbolUtils.matchesEqualsMethodSignature
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinEqualsHashCodeToStringSymbolUtils.matchesHashCodeMethodSignature
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.classVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClass

class ArrayInDataClassInspection : KotlinApplicableInspectionBase.Simple<KtParameter, ArrayInDataClassInspection.Context>() {
    class Context(
        val equals: String?,
        val hashCode: String?,
        val classKindText: String,
    )

    override fun getProblemDescription(element: KtParameter, context: Context): String =
        KotlinBundle.message("array.property.in.class.it.s.recommended.to.override.equals.hashcode", context.classKindText)

    override fun createQuickFix(element: KtParameter, context: Context): KotlinModCommandQuickFix<KtParameter> {
        return object : KotlinModCommandQuickFix<KtParameter>() {
            override fun getFamilyName(): String =
                KotlinBundle.message("generate.equals.and.hashcode.fix.text")

            override fun applyFix(project: Project, element: KtParameter, updater: ModPsiUpdater): Unit = with(context) {
                val psiFactory = KtPsiFactory(project, markGenerated = true)
                val containingClass = element.containingClass() ?: return
                if (equals != null) {
                    generateFunctionDeclarationInClass(psiFactory, containingClass, equals)
                }
                if (hashCode != null) {
                    generateFunctionDeclarationInClass(psiFactory, containingClass, hashCode)
                }
            }

            private fun generateFunctionDeclarationInClass(factory: KtPsiFactory, containingClass: KtClass, text: String) {
                val function = factory.createFunction(text)
                shortenReferences(containingClass.addMemberDeclaration(function))
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> {
        return classVisitor { klass ->
            if (klass.arrayPropertyClassKind() == null) return@classVisitor
            val constructor = klass.primaryConstructor ?: return@classVisitor

            for (parameter in constructor.valueParameters) {
                visitTargetElement(parameter, holder, isOnTheFly)
            }
        }
    }

    override fun isApplicableByPsi(element: KtParameter): Boolean {
        return element.hasValOrVar()
    }

    context(session: KaSession)
    override fun prepareContext(element: KtParameter): Context? {
        val parameterType = element.symbol.returnType
        if (!parameterType.isArrayOrPrimitiveArray) return null
        val containingClass = element.containingClass() ?: return null
        val classKind = containingClass.arrayPropertyClassKind() ?: return null

        return when (checkOverriddenEqualsAndHashCode(containingClass)) {
            EqualsHashCodeOverrides.HAS_EQUALS_AND_HASHCODE -> null
            EqualsHashCodeOverrides.HAS_EQUALS -> {
                val text = GenerateEqualsAndHashCodeUtils.generateHashCode(containingClass)
                Context(equals = null, hashCode = text, classKindText = classKind.text)
            }

            EqualsHashCodeOverrides.HAS_HASHCODE -> {
                val text = GenerateEqualsAndHashCodeUtils.generateEquals(containingClass)
                Context(equals = text, hashCode = null, classKindText = classKind.text)
            }

            EqualsHashCodeOverrides.HAS_NONE -> {
                val equalsText = GenerateEqualsAndHashCodeUtils.generateEquals(containingClass)
                val hashCodeText = GenerateEqualsAndHashCodeUtils.generateHashCode(containingClass)
                Context(equalsText, hashCodeText, classKind.text)
            }
        }
    }

    private fun KtClass.arrayPropertyClassKind(): ArrayPropertyClassKind? {
        return when {
            isData() -> ArrayPropertyClassKind.DATA
            isValue() -> ArrayPropertyClassKind.VALUE
            else -> null
        }
    }

    context(_: KaSession)
    private fun checkOverriddenEqualsAndHashCode(klass: KtClass): EqualsHashCodeOverrides {
        val functionSymbols = klass.declarations.mapNotNull { declaration ->
            (declaration as? KtFunction)?.symbol as? KaNamedFunctionSymbol
        }
        val overriddenEquals = functionSymbols.any { matchesEqualsMethodSignature(it) }
        val overriddenHashCode = functionSymbols.any { matchesHashCodeMethodSignature(it) }

        return EqualsHashCodeOverrides.of(overriddenEquals, overriddenHashCode)
    }

    private enum class ArrayPropertyClassKind(private val messageKey: String) {
        DATA("array.property.in.class.kind.data.class"),
        VALUE("array.property.in.class.kind.value.class");

        val text: String
            get() = KotlinBundle.message(messageKey)
    }

    private enum class EqualsHashCodeOverrides {
        HAS_EQUALS_AND_HASHCODE,
        HAS_EQUALS,
        HAS_HASHCODE,
        HAS_NONE;

        companion object {
            fun of(hasEquals: Boolean, hasHashCode: Boolean): EqualsHashCodeOverrides = when {
                hasEquals && hasHashCode -> HAS_EQUALS_AND_HASHCODE
                hasEquals -> HAS_EQUALS
                hasHashCode -> HAS_HASHCODE
                else -> HAS_NONE
            }
        }
    }
}
