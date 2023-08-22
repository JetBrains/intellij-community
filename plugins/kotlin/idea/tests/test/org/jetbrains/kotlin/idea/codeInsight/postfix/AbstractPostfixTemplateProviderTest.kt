// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractPostfixTemplateProviderTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun setUp() {
        super.setUp()
        KtPostfixTemplateProvider.previouslySuggestedExpressions = emptyList()
    }

    protected fun doTest(unused: String) {
        val testFile = dataFile()
        myFixture.configureByFile(testFile)

        val fileText = file.text
        val template = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// TEMPLATE:")
        if (template != null) {
            TemplateManagerImpl.setTemplateTesting(testRootDisposable)
            myFixture.type(template.replace("\\t", "\t"))
        } else {
            myFixture.type('\t')
        }

        val previouslySuggestedExpressions = KtPostfixTemplateProvider.previouslySuggestedExpressions
        if (previouslySuggestedExpressions.size > 1 && !InTextDirectivesUtils.isDirectiveDefined(fileText, "ALLOW_MULTIPLE_EXPRESSIONS")) {
            fail("Only one expression should be suggested, but $previouslySuggestedExpressions were found")
        }

        val expectedFile = File(testFile.parentFile, testFile.name + ".after")
        myFixture.checkResultByFile(expectedFile)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { super.tearDown() },
            ThrowableRunnable { KtPostfixTemplateProvider.previouslySuggestedExpressions = emptyList() }
        )
    }
}
