// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.ui.UiInterceptors
import com.intellij.ui.UiInterceptors.UiInterceptor
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import javax.swing.JComponent

class AutomaticTestMethodRenamerTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        val jarPaths = listOf(TestKotlinArtifacts.kotlinStdlib, ConfigLibraryUtil.ATTACHABLE_LIBRARIES["JUnit5"]!!)
        return KotlinWithJdkAndRuntimeLightProjectDescriptor(jarPaths, listOf(TestKotlinArtifacts.kotlinStdlibSources))
    }

    fun `test should not rename helper method in test class java`() {
        myFixture.configureByText(
            "ClassTest.java", """
        package a;
        
        import org.junit.jupiter.api.Test;
        
        public class ClassTest {
            @Test
            public void testCheckSome() {
                assert false;
            }
        
            private int helperMeth<caret>od() {
                return 0;
            }
        }
        """.trimIndent()
        )
        doTest()
    }

    fun `test should not rename helper method in test class kotlin`() {
        myFixture.configureByText(
            "ClassTest.kt", """
        import org.junit.jupiter.api.Test

        class ClassTest {
            @Test
            fun testCheckSome() {
            }
        
            private fun helperMethod<caret>(): Int {
                return 0
            }
        }
        """.trimIndent()
        )
        doTest()
    }

    private fun doTest() {
        UiInterceptors.registerPossible(myFixture.testRootDisposable, object : UiInterceptor<DialogWrapper>(DialogWrapper::class.java) {
            override fun doIntercept(component: DialogWrapper) {
                error("Renaming dialog should not be shown!")
            }

        })
        val element = myFixture.elementAtCaret


        val renameDialog = object : DialogWrapper(null, false) {
            override fun createCenterPanel(): JComponent = panel { }
        }

        Disposer.register(myFixture.testRootDisposable, renameDialog.disposable)

        val renameProcessor = object : RenameProcessor(myFixture.project, element, "superHelperMethod", false, false) {
            override fun showAutomaticRenamingDialog(automaticVariableRenamer: AutomaticRenamer?): Boolean {
                if (automaticVariableRenamer == null) return true
                renameDialog.show()
                return true
            }
        }
        AutomaticRenamerFactory.EP_NAME.extensionList.forEach {
            if (it.isApplicable(element)) renameProcessor.addRenamerFactory(it)
        }
        renameProcessor.run()
    }
}