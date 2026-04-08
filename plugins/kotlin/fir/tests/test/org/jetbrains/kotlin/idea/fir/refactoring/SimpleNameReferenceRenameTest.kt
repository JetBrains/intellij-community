// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("idea/tests")
@TestMetadata("testData/refactoring/rename/simpleNameReference")
class SimpleNameReferenceRenameTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    fun testRenameLabel() {
        doTest("foo")
    }

    fun testRenameLabel2() {
        doTest("anotherFoo")
    }

    fun testRenameField() {
        doTest("renamed")
    }

    fun testRenameFieldIdentifier() {
        doTest("anotherRenamed")
    }

    fun testMemberOfLocalObject() {
        doTest("bar")
    }

    fun testLocalFunction() {
        doTest("xyzzy")
    }

    fun testParameterOfCopyMethod() {
        doTest("y")
    }

    private fun doTest(newName: String) {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val element = TargetElementUtil
            .findTargetElement(
                editor,
                TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
            )
        assertNotNull(element)
        RenameProcessor(project, element!!, newName, true, true).run()
        myFixture.checkResultByFile(getTestName(true) + ".kt.after")
    }
}
