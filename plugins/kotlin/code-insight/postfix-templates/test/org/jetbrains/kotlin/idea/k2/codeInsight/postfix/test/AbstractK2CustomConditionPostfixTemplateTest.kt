// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.postfix.test

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.codeInsight.postfix.AbstractKotlinPostfixTemplateTestBase
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinEditablePostfixTemplate
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateBooleanExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateExpressionFqnCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNonUnitExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNotNullableExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNullableExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNumberExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateUnitExpressionCondition
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import java.nio.file.Paths

/**
 * Test base for custom (editable) Kotlin postfix templates that are gated by
 * [KotlinPostfixTemplateExpressionCondition]s.
 *
 * Each test registers a [KotlinEditablePostfixTemplate] (keyed by the test-data directory name, which
 * [performTest] types as `.<key>`) built from directives in the test file, then runs the normal
 * expansion/assert flow. Directives:
 * - `// TEMPLATE_TEXT: if($EXPR$)` — the editable template body (required).
 * - `// CONDITION: kotlin.boolean` — repeatable; the applicable-expression-type condition(s).
 *    Use `kotlin.fqn:<fqn>` for the FQN condition. No condition means "always applicable".
 * - `// USE_TOPMOST: true` — optional; defaults to `false`.
 */
abstract class AbstractK2CustomConditionPostfixTemplateTest : AbstractKotlinPostfixTemplateTestBase() {
    override fun registerAdditionalTemplates(fileText: String) {
        val templateText = InTextDirectivesUtils.findStringWithPrefixes(fileText, TEMPLATE_TEXT_DIRECTIVE)
            ?: error("Custom postfix template test must declare `// $TEMPLATE_TEXT_DIRECTIVE <body>`")
        val useTopmost = InTextDirectivesUtils.findStringWithPrefixes(fileText, USE_TOPMOST_DIRECTIVE)?.toBoolean() ?: false
        val conditions = InTextDirectivesUtils.findListWithPrefixes(fileText, CONDITION_DIRECTIVE)
            .map { parseCondition(it) }
            .toSet()

        val key = Paths.get(testDataPath).fileName.toString()
        val provider = kotlinPostfixTemplateProvider
        val template = KotlinEditablePostfixTemplate(
            /* templateId = */ key,
            /* templateName = */ key,
            /* templateText = */ templateText,
            /* example = */ "",
            /* expressionConditions = */ conditions,
            /* useTopmostExpression = */ useTopmost,
            /* provider = */ provider,
        )

        val storage = PostfixTemplateStorage.getInstance()
        val original = storage.getTemplates(provider)
        storage.setTemplates(provider, original + template)
        Disposer.register(testRootDisposable) { storage.setTemplates(provider, original) }
    }

    private fun parseCondition(id: String): KotlinPostfixTemplateExpressionCondition = when {
        id == KotlinPostfixTemplateUnitExpressionCondition.id -> KotlinPostfixTemplateUnitExpressionCondition
        id == KotlinPostfixTemplateNonUnitExpressionCondition.id -> KotlinPostfixTemplateNonUnitExpressionCondition
        id == KotlinPostfixTemplateBooleanExpressionCondition.id -> KotlinPostfixTemplateBooleanExpressionCondition
        id == KotlinPostfixTemplateNumberExpressionCondition.id -> KotlinPostfixTemplateNumberExpressionCondition
        id == KotlinPostfixTemplateNullableExpressionCondition.id -> KotlinPostfixTemplateNullableExpressionCondition
        id == KotlinPostfixTemplateNotNullableExpressionCondition.id -> KotlinPostfixTemplateNotNullableExpressionCondition
        id.startsWith(FQN_CONDITION_PREFIX) -> KotlinPostfixTemplateExpressionFqnCondition(id.removePrefix(FQN_CONDITION_PREFIX))
        else -> error("Unknown postfix template condition id: `$id`")
    }

    private companion object {
        const val TEMPLATE_TEXT_DIRECTIVE: String = "TEMPLATE_TEXT:"
        const val CONDITION_DIRECTIVE: String = "CONDITION:"
        const val USE_TOPMOST_DIRECTIVE: String = "USE_TOPMOST:"
        const val FQN_CONDITION_PREFIX: String = "kotlin.fqn:"
    }
}
