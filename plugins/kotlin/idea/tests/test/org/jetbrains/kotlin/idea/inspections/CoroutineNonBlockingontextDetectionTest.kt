// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

private val ktProjectDescriptor = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
    listOf(TestKotlinArtifacts.kotlinStdlib), listOf(TestKotlinArtifacts.kotlinStdlibSources)
) {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
        super.configureModule(module, model)
        MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
        MavenDependencyUtil.addFromMaven(model, "org.jetbrains:annotations:23.0.0")
    }
}

abstract class AbstractCoroutineNonBlockingContextDetectionTest(
    private val considerUnknownAsBlocking: Boolean
) : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = ktProjectDescriptor

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(BlockingMethodInNonBlockingContextInspection(considerUnknownAsBlocking))
    }
}

@TestRoot("idea/tests")
@TestMetadata("testData/inspections/blockingCallsDetection")
@RunWith(JUnit38ClassRunner::class)
class CoroutineNonBlockingContextDetectionTest : AbstractCoroutineNonBlockingContextDetectionTest(false) {
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

@TestRoot("idea/tests")
@TestMetadata("testData/inspections/blockingCallsDetection")
@RunWith(JUnit38ClassRunner::class)
class CoroutineNonBlockingContextDetectionWithUnsureAsBlockingTest : AbstractCoroutineNonBlockingContextDetectionTest(true) {
    fun testCoroutineScope() {
        myFixture.configureByFile("InsideCoroutineUnsure.kt")
        myFixture.testHighlighting(true, false, false, "InsideCoroutineUnsure.kt")
    }

    fun testLambdaInSuspendDeclaration() {
        myFixture.configureByFile("LambdaAssignmentCheckUnsure.kt")
        myFixture.testHighlighting(true, false, false, "LambdaAssignmentCheckUnsure.kt")
    }

    fun testDispatchersTypeDetection() {
        myFixture.configureByFile("DispatchersTypeCheckUnsure.kt")
        myFixture.testHighlighting(true, false, false, "DispatchersTypeCheckUnsure.kt")
    }
}