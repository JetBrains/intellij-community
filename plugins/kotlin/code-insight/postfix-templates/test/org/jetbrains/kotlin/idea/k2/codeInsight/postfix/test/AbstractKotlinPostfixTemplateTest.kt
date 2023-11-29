// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.postfix.test

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.relativeTo

abstract class AbstractKotlinPostfixTemplateTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJvmLightProjectDescriptor.DEFAULT
    }

    protected fun performTest() {
        IgnoreTests.runTestIfNotDisabledByFileDirective(testRootPath.resolve(testMethodPath), IgnoreTests.DIRECTIVES.IGNORE_K2, "after") {
            myFixture.configureByDefaultFile()
            templateName?.let { myFixture.type(".$it") }
            myFixture.type("\t")
            myFixture.checkContentByExpectedPath(".after", addSuffixAfterExtension = isOldTestData)
        }
    }

    private val templateName: String?
        get() = if (!isOldTestData) Paths.get(testDataPath).name else null

    private val isOldTestData: Boolean
        get() = Paths.get(testDataPath)
            .relativeTo(KotlinTestHelpers.getTestRootPath(javaClass))
            .toString()
            .contains("oldTestData")
}