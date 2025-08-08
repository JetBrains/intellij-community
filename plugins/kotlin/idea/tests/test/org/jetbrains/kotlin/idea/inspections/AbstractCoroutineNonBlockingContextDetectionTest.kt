// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

private val ktProjectDescriptor = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
    listOf(TestKotlinArtifacts.kotlinStdlib), listOf(TestKotlinArtifacts.kotlinStdlibSources)
) {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
        super.configureModule(module, model)
        MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
        MavenDependencyUtil.addFromMaven(model, "org.jetbrains:annotations:23.0.0")
    }
}

abstract class AbstractCoroutineNonBlockingContextDetectionTest : KotlinLightCodeInsightFixtureTestCase() {
    private val CONSIDER_UNKNOWN_AS_BLOCKING: String = "CONSIDER_UNKNOWN_AS_BLOCKING:"
    private val CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: String = "CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING:"

    override fun getProjectDescriptor(): LightProjectDescriptor = ktProjectDescriptor

    protected fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFilePath(), IgnoreTests.DIRECTIVES.of(pluginMode)) {
            doTest()
        }
    }
    
    private fun doTest() {
        val fileName = fileName()

        val dataFile = dataFile(fileName)
        val fileText = dataFile.readText()

        val considerUnknownAsBlocking = 
            InTextDirectivesUtils.getPrefixedBoolean(fileText, CONSIDER_UNKNOWN_AS_BLOCKING)
                ?: error("No '$CONSIDER_UNKNOWN_AS_BLOCKING' directive found")

        val considerSuspendContextNonBlocking = 
            InTextDirectivesUtils.getPrefixedBoolean(fileText, CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING)
                ?: error("No '$CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING' directive found")
        
        val inspection = BlockingMethodInNonBlockingContextInspection(considerUnknownAsBlocking, considerSuspendContextNonBlocking)
        myFixture.enableInspections(inspection)

        try {
            myFixture.configureByFile(fileName)
            myFixture.testHighlighting(true, false, false, fileName)
        } finally {
            myFixture.disableInspections(inspection)
        }
    }
}
