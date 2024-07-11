// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor

class SimplifiableCallChainInspection : KotlinApplicableInspectionBase.Simple<KtQualifiedExpression, Unit>() {
    context(KaSession) override fun prepareContext(element: KtQualifiedExpression): Unit? {
        return null
    }

    override fun getProblemDescription(element: KtQualifiedExpression, context: Unit): String {
        return KotlinBundle.message("call.chain.on.collection.type.may.be.simplified")
    }

    override fun createQuickFix(element: KtQualifiedExpression, context: Unit): KotlinModCommandQuickFix<KtQualifiedExpression> {
        return object : KotlinModCommandQuickFix<KtQualifiedExpression>() {
            override fun getFamilyName(): String {
                return KotlinBundle.message("call.chain.on.collection.type.may.be.simplified")
            }

            override fun applyFix(project: Project, element: KtQualifiedExpression, updater: ModPsiUpdater) {

            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> {
        return qualifiedExpressionVisitor { qualifiedExpression ->
            qualifiedExpression.languageVersionSettings
        }
    }
}
