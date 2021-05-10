// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.TestMetadata
import org.jetbrains.kotlin.idea.test.TestRoot
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

private val ktProjectDescriptor = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
    listOf(KotlinArtifacts.instance.kotlinStdlib), listOf(KotlinArtifacts.instance.kotlinStdlibSources)
) {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
        super.configureModule(module, model)
        MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
        MavenDependencyUtil.addFromMaven(model, "org.jetbrains:annotations:23.0.0")
    }
}

@TestRoot("idea/tests")
@TestMetadata("testData/inspections/blockingCallsDetection")
@RunWith(JUnit38ClassRunner::class)
class CoroutineNonBlockingContextDetectionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = ktProjectDescriptor

    override fun setUp() {
        super.setUp()
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
        myFixture.testHighlighting(true, false, false, "FlowOn.kt")
    }
}