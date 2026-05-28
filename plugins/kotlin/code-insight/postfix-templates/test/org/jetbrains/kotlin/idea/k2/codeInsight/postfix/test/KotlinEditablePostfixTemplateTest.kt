// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.postfix.test

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.kotlin.idea.codeInsight.postfix.KotlinPostfixTemplateProvider
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinEditablePostfixTemplate
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateBooleanExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateExpressionFqnCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNonUnitExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNotNullableExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNullableExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateNumberExpressionCondition
import org.jetbrains.kotlin.idea.codeInsight.postfix.editable.KotlinPostfixTemplateExpressionCondition.KotlinPostfixTemplateUnitExpressionCondition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class KotlinEditablePostfixTemplateTest {
    private val provider = KotlinPostfixTemplateProvider()

    @Test
    fun testId() {
        val template = templateWithConditions("myId", "myKey")
        assertEquals("myId", reloadKotlinTemplate(template).id)
    }

    @Test
    fun testTemplateKey() {
        val template = templateWithConditions("myId", "myKey")
        assertEquals(".myKey", reloadKotlinTemplate(template).key)
    }

    @Test
    fun testTopmost() {
        val template1 = templateWithConditions(useTopmostExpression = true)
        assertTrue(reloadKotlinTemplate(template1).isUseTopmostExpression)
        val template2 = templateWithConditions(useTopmostExpression = false)
        assertFalse(reloadKotlinTemplate(template2).isUseTopmostExpression)
    }

    @Test
    fun testUnitCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateUnitExpressionCondition)
    }

    @Test
    fun testNonUnitCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateNonUnitExpressionCondition)
    }

    @Test
    fun testBooleanCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateBooleanExpressionCondition)
    }

    @Test
    fun testNumberCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateNumberExpressionCondition)
    }

    @Test
    fun testNullableCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateNullableExpressionCondition)
    }

    @Test
    fun testNotNullableCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateNotNullableExpressionCondition)
    }

    @Test
    fun testFqnCondition() {
        val condition = KotlinPostfixTemplateExpressionFqnCondition("test.class.Name")
        val conditions = reloadConditions(templateWithConditions(conditions = setOf(condition)))
        assertEquals(conditions.firstOrNull(), condition)
        assertEquals("test.class.Name", conditions.first().getPresentableName())
    }

    private fun assertConditionRoundtrip(condition: KotlinPostfixTemplateExpressionCondition) {
        assertEquals(reloadConditions(templateWithConditions(conditions = setOf(condition))).firstOrNull(), condition)
    }

    private fun templateWithConditions(
        templateId: String = "myId",
        templateName: String = "myKey",
        conditions: Set<KotlinPostfixTemplateExpressionCondition> = emptySet(),
        useTopmostExpression: Boolean = true,
    ): KotlinEditablePostfixTemplate =
        KotlinEditablePostfixTemplate(templateId, templateName, "", "", conditions, useTopmostExpression, provider)

    private fun reloadTemplate(template: PostfixTemplate): PostfixTemplate {
        val saveStorage = PostfixTemplateStorage()
        saveStorage.setTemplates(provider, listOf(template))
        val loadStorage = PostfixTemplateStorage.getInstance()
        loadStorage.loadState(saveStorage.getState()!!)
        return loadStorage.getTemplates(provider).first()
    }

    private fun reloadKotlinTemplate(template: KotlinEditablePostfixTemplate): KotlinEditablePostfixTemplate =
        reloadTemplate(template) as KotlinEditablePostfixTemplate

    private fun reloadConditions(template: KotlinEditablePostfixTemplate): Set<KotlinPostfixTemplateExpressionCondition> =
        reloadKotlinTemplate(template).expressionConditions
}
