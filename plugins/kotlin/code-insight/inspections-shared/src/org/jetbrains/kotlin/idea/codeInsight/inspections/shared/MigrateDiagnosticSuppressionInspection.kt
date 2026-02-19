// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.annotationEntryVisitor

internal class MigrateDiagnosticSuppressionInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return annotationEntryVisitor(fun(annotationEntry) {
            if (!isSuppressAnnotation(annotationEntry)) return

            for (argument in annotationEntry.valueArguments) {
                val expression = argument.getArgumentExpression() as? KtStringTemplateExpression ?: continue
                val text = expression.text
                if (text.firstOrNull() != '\"' || text.lastOrNull() != '\"') continue
                val unquotedText = StringUtil.unquoteString(text)
                val newDiagnosticFactory = Holder.MIGRATION_MAP[unquotedText] ?: continue

                holder.registerProblem(
                    expression,
                    KotlinBundle.message("diagnostic.name.should.be.replaced.by.the.new.one"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    ReplaceDiagnosticNameFix(newDiagnosticFactory)
                )
            }
        })
    }

    private fun isSuppressAnnotation(annotationEntry: KtAnnotationEntry): Boolean {
        val calleeExpression = annotationEntry.calleeExpression ?: return false
        if (calleeExpression.text != "Suppress") return false

        analyze(calleeExpression) {
            val constructorCall = calleeExpression.resolveToCall()?.singleConstructorCallOrNull() ?: return false
            val fqName = constructorCall.symbol.importableFqName ?: return false

            return fqName == StandardNames.FqNames.suppress
        }
    }

    class ReplaceDiagnosticNameFix(private val diagnosticFactoryName: String) : LocalQuickFix {
        override fun getName(): String = KotlinBundle.message("replace.diagnostic.name.fix.text", familyName, diagnosticFactoryName)

        override fun getFamilyName(): String = KotlinBundle.message("replace.diagnostic.name.fix.family.name")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtStringTemplateExpression ?: return
            if (!FileModificationService.getInstance().preparePsiElementForWrite(expression)) return

            val psiFactory = KtPsiFactory(project)
            expression.replace(psiFactory.createExpression("\"$diagnosticFactoryName\""))
        }
    }

    private object Holder {
        val MIGRATION_MAP: Map<String, String> = mapOf(
            "HEADER_DECLARATION_WITH_BODY" to "EXPECTED_DECLARATION_WITH_BODY",
            "HEADER_CLASS_CONSTRUCTOR_DELEGATION_CALL" to "EXPECTED_CLASS_CONSTRUCTOR_DELEGATION_CALL",
            "HEADER_CLASS_CONSTRUCTOR_PROPERTY_PARAMETER" to "EXPECTED_CLASS_CONSTRUCTOR_PROPERTY_PARAMETER",
            "HEADER_ENUM_CONSTRUCTOR" to "EXPECTED_ENUM_CONSTRUCTOR",
            "HEADER_ENUM_ENTRY_WITH_BODY" to "EXPECTED_ENUM_ENTRY_WITH_BODY",
            "HEADER_PROPERTY_INITIALIZER" to "EXPECTED_PROPERTY_INITIALIZER",

            "IMPL_TYPE_ALIAS_NOT_TO_CLASS" to "ACTUAL_TYPE_ALIAS_NOT_TO_CLASS",
            "IMPL_TYPE_ALIAS_TO_CLASS_WITH_DECLARATION_SITE_VARIANCE" to "ACTUAL_TYPE_ALIAS_TO_CLASS_WITH_DECLARATION_SITE_VARIANCE",
            "IMPL_TYPE_ALIAS_WITH_USE_SITE_VARIANCE" to "ACTUAL_TYPE_ALIAS_WITH_USE_SITE_VARIANCE",
            "IMPL_TYPE_ALIAS_WITH_COMPLEX_SUBSTITUTION" to "ACTUAL_TYPE_ALIAS_WITH_COMPLEX_SUBSTITUTION",

            "HEADER_WITHOUT_IMPLEMENTATION" to "NO_ACTUAL_FOR_EXPECT",
            "IMPLEMENTATION_WITHOUT_HEADER" to "ACTUAL_WITHOUT_EXPECT",

            "HEADER_CLASS_MEMBERS_ARE_NOT_IMPLEMENTED" to "NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS",
            "IMPL_MISSING" to "ACTUAL_MISSING"
        )
    }
}