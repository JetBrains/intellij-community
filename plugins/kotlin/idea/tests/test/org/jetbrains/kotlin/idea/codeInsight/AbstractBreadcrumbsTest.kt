// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightPlatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractBreadcrumbsTest : KotlinLightPlatformCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor? = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    protected open fun doTest(unused: String) {
        val fileName = fileName()
        assert(fileName.endsWith(".kt")) { fileName }
        myFixture.configureByFile(fileName)

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = KotlinBreadcrumbsInfoProvider()
        val elements = generateSequence(element) { provider.getParent(it) }
            .filter { provider.acceptElement(it) }
            .toList()
            .asReversed()
        val crumbs = elements.joinToString(separator = "\n") { "  " + provider.getElementInfo(it) }
        val tooltips = elements.joinToString(separator = "\n") { "  " + provider.getElementTooltip(it) }
        val resultText = "Crumbs:\n$crumbs\nTooltips:\n$tooltips"
        KotlinTestUtils.assertEqualsToFile(dataFile(File(fileName).nameWithoutExtension + ".txt"), resultText)
    }
}