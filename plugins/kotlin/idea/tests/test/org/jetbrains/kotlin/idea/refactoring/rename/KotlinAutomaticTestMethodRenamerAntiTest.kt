// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("idea/tests")
@TestMetadata("testData/refactoring/renameTestMethod")
class KotlinAutomaticTestMethodRenamerAntiTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        val jarPaths = listOf(TestKotlinArtifacts.kotlinStdlib, ConfigLibraryUtil.ATTACHABLE_LIBRARIES["JUnit5"]!!)
        return object : KotlinWithJdkAndRuntimeLightProjectDescriptor(jarPaths, listOf(TestKotlinArtifacts.kotlinStdlibSources)) {
            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)
                contentEntry.addSourceFolder(contentEntry.url + "/${getTestName(true)}/test_src", true)
            }
        }
    }

    fun testTestMethod() = doTest("ClassTest.kt")

    fun testHelperMethod() = doTest("ClassTest.kt")

    fun testHelperClass() = doTest("TestUtil.kt")

    private fun doTest(filename : String) {
        val filePath = "${getTestName(true)}/test_src/$filename"
        myFixture.configureByFile(filePath)
        val element = myFixture.elementAtCaret
        AutomaticRenamerFactory.EP_NAME.extensionList.forEach {
         TestCase.assertFalse(it.isApplicable(element))
        }
    }
}