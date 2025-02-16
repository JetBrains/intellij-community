// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isNullableAnyType
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ArrayInDataClassInspection : KotlinApplicableInspectionBase.Simple<KtParameter, ArrayInDataClassInspection.Context>() {
    class Context(
        val equals: String?,
        val hashCode: String?,
    )

    override fun getProblemDescription(element: KtParameter, context: Context): String {
        return KotlinBundle.message("array.property.in.data.class.it.s.recommended.to.override.equals.hashcode")
    }

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
                shortenReferences(containingClass.addDeclaration(function))
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> {
        return classVisitor { klass ->
            if (!klass.isData()) return@classVisitor
            val constructor = klass.primaryConstructor ?: return@classVisitor

            for (parameter in constructor.valueParameters) {
                visitTargetElement(parameter, holder, isOnTheFly)
            }
        }
    }

    override fun isApplicableByPsi(element: KtParameter): Boolean {
        return element.hasValOrVar()
    }

    override fun KaSession.prepareContext(element: KtParameter): Context? {
        val parameterType = element.symbol.returnType
        if (!parameterType.isArrayOrPrimitiveArray) return null
        val containingClass = element.containingClass() ?: return null

        return when (checkOverriddenEqualsAndHashCode(containingClass)) {
            EqualsHashCodeOverrides.HAS_EQUALS_AND_HASHCODE -> null
            EqualsHashCodeOverrides.HAS_EQUALS -> {
                val text = GenerateEqualsAndHashCodeUtils.generateHashCode(containingClass)
                Context(equals = null, hashCode = text)
            }
            EqualsHashCodeOverrides.HAS_HASHCODE -> {
                val text = GenerateEqualsAndHashCodeUtils.generateEquals(containingClass)
                Context(equals = text, hashCode = null)
            }
            EqualsHashCodeOverrides.HAS_NONE -> {
                val equalsText = GenerateEqualsAndHashCodeUtils.generateEquals(containingClass)
                val hashCodeText = GenerateEqualsAndHashCodeUtils.generateHashCode(containingClass)
                Context(equalsText, hashCodeText)
            }
        }
    }

    private fun checkOverriddenEqualsAndHashCode(klass: KtClass): EqualsHashCodeOverrides {
        var overriddenEquals = false
        var overriddenHashCode = false
        for (declaration in klass.declarations) {
            if (declaration !is KtFunction) continue
            if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) continue
            if (declaration.nameAsName == OperatorNameConventions.EQUALS && declaration.valueParameters.size == 1) {
                analyze(declaration) {
                    val parameterType = declaration.symbol.safeAs<KaFunctionSymbol>()?.valueParameters?.singleOrNull()?.returnType
                    if (parameterType?.isNullableAnyType() == true) {
                        overriddenEquals = true
                    }
                }
            }
            if (declaration.nameAsName == OperatorNameConventions.HASH_CODE && declaration.valueParameters.isEmpty()) {
                overriddenHashCode = true
            }
        }

        return EqualsHashCodeOverrides.of(overriddenEquals, overriddenHashCode)
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
