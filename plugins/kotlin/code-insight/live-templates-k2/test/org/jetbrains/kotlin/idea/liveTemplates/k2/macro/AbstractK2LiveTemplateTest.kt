// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.util.application.executeCommand
import java.nio.file.Paths
import kotlin.io.path.name

abstract class AbstractK2LiveTemplateTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJvmLightProjectDescriptor.DEFAULT
    }

    protected fun performTest() {
        val disableDirective = when (pluginKind) {
            KotlinPluginMode.K1 -> IgnoreTests.DIRECTIVES.IGNORE_K1
            KotlinPluginMode.K2 -> IgnoreTests.DIRECTIVES.IGNORE_K2
        }
        IgnoreTests.runTestIfNotDisabledByFileDirective(testRootPath.resolve(testMethodPath), disableDirective, "after") {
            myFixture.configureByDefaultFile()
            templateName?.let(myFixture::type)

            val fileText = file.text
            val template = InTextDirectivesUtils.findStringWithPrefixes(fileText, TEMPLATE_DIRECTIVE)

            if (template != null) {
                myFixture.type(template.replace("\\t", "\t"))
            } else {
                myFixture.type("\t")
            }
            myFixture.checkContentByExpectedPath(".after")
        }
        val templateState = TemplateManagerImpl.getTemplateState(editor)
        if (templateState?.isFinished() == false) {
            project.executeCommand("") { templateState.gotoEnd(false) }
        }
    }

    private val templateName: String?
        get() = Paths.get(testDataPath).name

    companion object {
        const val TEMPLATE_DIRECTIVE = "TEMPLATE:"
    }
}