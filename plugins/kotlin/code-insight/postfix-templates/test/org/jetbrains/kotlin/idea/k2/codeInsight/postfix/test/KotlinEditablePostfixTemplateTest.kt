// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.postfix.test

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.testFramework.LightPlatformTestCase
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

class KotlinEditablePostfixTemplateTest: LightPlatformTestCase() {
    private val provider = KotlinPostfixTemplateProvider()

    fun testId() {
        val template = templateWithConditions("myId", "myKey")
        assertEquals("myId", reloadKotlinTemplate(template).id)
    }

    fun testTemplateKey() {
        val template = templateWithConditions("myId", "myKey")
        assertEquals(".myKey", reloadKotlinTemplate(template).key)
    }

    fun testTopmost() {
        val template1 = templateWithConditions(useTopmostExpression = true)
        assertTrue(reloadKotlinTemplate(template1).isUseTopmostExpression)
        val template2 = templateWithConditions(useTopmostExpression = false)
        assertFalse(reloadKotlinTemplate(template2).isUseTopmostExpression)
    }

    fun testUnitCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateUnitExpressionCondition)
    }

    fun testNonUnitCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateNonUnitExpressionCondition)
    }

    fun testBooleanCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateBooleanExpressionCondition)
    }

    fun testNumberCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateNumberExpressionCondition)
    }

    fun testNullableCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateNullableExpressionCondition)
    }

    fun testNotNullableCondition() {
        assertConditionRoundtrip(KotlinPostfixTemplateNotNullableExpressionCondition)
    }

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
