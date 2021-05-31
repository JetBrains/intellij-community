// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.test.TestRoot
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.instance as KotlinArtifacts

@TestRoot("idea/tests")
@TestMetadata("testData/inspections/blockingCallsDetection")
@RunWith(JUnit38ClassRunner::class)
class CoroutineNonBlockingContextDetectionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor =
        object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
            listOf(KotlinArtifacts.kotlinStdlib, KotlinArtifacts.kotlinCoroutinesExperimentalCompat),
            listOf(KotlinArtifacts.kotlinStdlibSources)
        ) {
            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)
                MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
            }
        }

    override fun setUp() {
        super.setUp()
        myFixture.addClass("""package org.jetbrains.annotations; public @interface BlockingContext {}""")
        myFixture.enableInspections(BlockingMethodInNonBlockingContextInspection::class.java)
    }

    fun testSimpleCoroutineScope() {
        doTest("InsideCoroutine.kt")
    }

    fun testCoroutineContextCheck() {
        doTest("ContextCheck.kt")
    }

    fun testLambdaReceiverType() {
        doTest("LambdaReceiverTypeCheck.kt")
    }

    fun testNestedFunctionsInsideSuspendLambda() {
        doTest("NestedFunctionsInsideSuspendLambda.kt")
    }

    fun testDispatchersTypeDetection() {
        doTest("DispatchersTypeCheck.kt")
    }

    private fun doTest(fileName: String) {
        myFixture.configureByFile(fileName)
        myFixture.testHighlighting(true, false, false, fileName)
    }

    fun testLambdaInSuspendDeclaration() {
        myFixture.configureByFile("LambdaAssignmentCheck.kt")
        myFixture.testHighlighting(true, false, false, "LambdaAssignmentCheck.kt")
    }

    fun testFlowOn() {
        myFixture.configureByFile("FlowOn.kt")
        myFixture.testHighlighting("FlowOn.kt")
    }
}