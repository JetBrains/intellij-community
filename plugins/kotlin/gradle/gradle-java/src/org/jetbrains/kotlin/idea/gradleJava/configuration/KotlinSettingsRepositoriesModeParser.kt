// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.SettingsRepositoriesMode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil

internal fun KtBlockExpression.getSettingsRepositoriesMode(): SettingsRepositoriesMode? =
    statements.firstNotNullOfOrNull(::parseRepositoriesMode)

private fun parseRepositoriesMode(statement: KtExpression): SettingsRepositoriesMode? =
    when (statement) {
        is KtBinaryExpression -> parseRepositoriesModeAssignment(statement)
        is KtDotQualifiedExpression -> parseRepositoriesModeSetter(statement)
        else -> null
    }

private fun parseRepositoriesModeAssignment(assignment: KtBinaryExpression): SettingsRepositoriesMode? {
    if (assignment.operationReference.getReferencedName() != "=") return null

    val property = assignment.left as? KtNameReferenceExpression ?: return null
    if (property.getReferencedName() != "repositoriesMode") return null

    return assignment.right.extractRepositoriesMode()
}

private fun parseRepositoriesModeSetter(expression: KtDotQualifiedExpression): SettingsRepositoriesMode? {
    val property = expression.receiverExpression as? KtNameReferenceExpression ?: return null
    if (property.getReferencedName() != "repositoriesMode") return null

    val call = expression.selectorExpression as? KtCallExpression ?: return null
    val callee = call.calleeExpression as? KtNameReferenceExpression ?: return null
    if (callee.getReferencedName() != "set") return null

    return call.valueArguments.singleOrNull()?.getArgumentExpression().extractRepositoriesMode()
}

private fun KtExpression?.extractRepositoriesMode(): SettingsRepositoriesMode? {
    val expression = KtPsiUtil.deparenthesize(this) ?: return null
    val modeName = when (expression) {
        is KtNameReferenceExpression -> expression.getReferencedName()
        is KtDotQualifiedExpression ->
            (expression.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
        else -> null
    } ?: return null

    return SettingsRepositoriesMode.entries.firstOrNull { it.name == modeName }
}
