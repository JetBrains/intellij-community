// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.liveTemplates

import com.intellij.codeInsight.template.impl.TemplateContextTypes
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.liveTemplates.KotlinTemplateContextType.*
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("code-insight/live-templates-k1")
@TestMetadata("testData/context")
@RunWith(JUnit38ClassRunner::class)
class LiveTemplatesContextTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.K1

    @TestMetadata("InDocComment.kt")
    fun testInDocComment() {
        assertInContexts(Generic::class.java, Comment::class.java)
    }

    @TestMetadata("TopLevel.kt")
    fun testTopLevel() {
        assertInContexts(Generic::class.java, TopLevel::class.java)
    }

    @TestMetadata("InExpression.kt")
    fun testInExpression() {
        assertInContexts(Generic::class.java, Expression::class.java)
    }

    @TestMetadata("AnonymousObject.kt")
    fun testAnonymousObject() {
        assertInContexts(Generic::class.java, Class::class.java)
    }

    @TestMetadata("CompanionObject.kt")
    fun testCompanionObject() {
        assertInContexts(Generic::class.java, Class::class.java, ObjectDeclaration::class.java)
    }

    @TestMetadata("LocalObject.kt")
    fun testLocalObject() {
        assertInContexts(Generic::class.java, Class::class.java, ObjectDeclaration::class.java)
    }

    @TestMetadata("ObjectInClass.kt")
    fun testObjectInClass() {
        assertInContexts(Generic::class.java, Class::class.java, ObjectDeclaration::class.java)
    }

    @TestMetadata("ObjectInObject.kt")
    fun testObjectInObject() {
        assertInContexts(Generic::class.java, Class::class.java, ObjectDeclaration::class.java)
    }

    @TestMetadata("TopLevelObject.kt")
    fun testTopLevelObject() {
        assertInContexts(Generic::class.java, Class::class.java, ObjectDeclaration::class.java)
    }

    @TestMetadata("StatementInBlock.kt")
    fun testStatementInBlock() {
        assertInContexts(Generic::class.java, Statement::class.java, Expression::class.java)
    }

    @TestMetadata("StatementInDoWhile.kt")
    fun testStatementInDoWhile() {
        assertInContexts(Generic::class.java, Statement::class.java, Expression::class.java)
    }

    @TestMetadata("StatementInFor.kt")
    fun testStatementInFor() {
        assertInContexts(Generic::class.java, Statement::class.java, Expression::class.java)
    }

    @TestMetadata("StatementInIfElse.kt")
    fun testStatementInIfElse() {
        assertInContexts(Generic::class.java, Statement::class.java, Expression::class.java)
    }

    @TestMetadata("StatementInIfThen.kt")
    fun testStatementInIfThen() {
        assertInContexts(Generic::class.java, Statement::class.java, Expression::class.java)
    }

    @TestMetadata("StatementInWhile.kt")
    fun testStatementInWhile() {
        assertInContexts(Generic::class.java, Statement::class.java, Expression::class.java)
    }

    @TestMetadata("StatementInWhen.kt")
    fun testStatementInWhen() {
        assertInContexts(Generic::class.java, Statement::class.java, Expression::class.java)
    }

    private fun assertInContexts(vararg expectedContexts: java.lang.Class<out KotlinTemplateContextType>) {
        myFixture.configureByDefaultFile()
        val allContexts = TemplateContextTypes.getAllContextTypes().filterIsInstance<KotlinTemplateContextType>()
        val enabledContexts = allContexts.filter { it.isInContext(myFixture.file, myFixture.caretOffset) }.map { it::class.java }
        UsefulTestCase.assertSameElements(enabledContexts, *expectedContexts)
    }
}
