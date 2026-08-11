// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.groovy

import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.SettingsRepositoriesMode
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression

internal fun GrClosableBlock.getSettingsRepositoriesMode(): SettingsRepositoriesMode? =
    statements.firstNotNullOfOrNull(::parseRepositoriesMode)

private fun parseRepositoriesMode(statement: GrStatement): SettingsRepositoriesMode? =
    when (statement) {
        is GrAssignmentExpression -> parseRepositoriesModeAssignment(statement)
        is GrMethodCallExpression -> parseRepositoriesModeSetter(statement)
        else -> null
    }

private fun parseRepositoriesModeAssignment(assignment: GrAssignmentExpression): SettingsRepositoriesMode? {
    val left = assignment.lValue as? GrReferenceExpression ?: return null
    if (left.referenceName != "repositoriesMode") return null

    return assignment.rValue?.extractRepositoriesMode()
}

private fun parseRepositoriesModeSetter(call: GrMethodCallExpression): SettingsRepositoriesMode? {
    val invoked = call.invokedExpression as? GrReferenceExpression ?: return null
    if (invoked.referenceName != "set") return null

    val receiver = invoked.qualifierExpression as? GrReferenceExpression ?: return null
    if (receiver.referenceName != "repositoriesMode") return null

    return call.expressionArguments.singleOrNull()?.extractRepositoriesMode()
}

private fun GrExpression.extractRepositoriesMode(): SettingsRepositoriesMode? {
    val modeName = when (this) {
        is GrReferenceExpression -> referenceName
        else -> text.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
    } ?: return null

    return SettingsRepositoriesMode.entries.firstOrNull { it.name == modeName }
}
