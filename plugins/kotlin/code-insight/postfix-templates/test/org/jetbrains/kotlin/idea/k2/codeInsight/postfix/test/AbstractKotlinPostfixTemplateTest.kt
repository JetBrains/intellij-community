// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.postfix.test

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.nio.file.Paths
import kotlin.io.path.name

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
        IgnoreTests.runTestIfNotDisabledByFileDirective(testRootPath.resolve(testMethodPath), IgnoreTests.DIRECTIVES.IGNORE_FIR, "after") {
            myFixture.configureByDefaultFile()
            myFixture.type(".$templateName\t")
            myFixture.checkContentByExpectedPath(".after")
        }
    }

    private val templateName: String
        get() = Paths.get(testDataPath).name
}