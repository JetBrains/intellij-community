// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.asJava

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiResolveHelper
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinCompiledLightClassesJavaAccessibilityTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptor

    fun testClassAccessibleFromJava() {
        myFixture.configureByText(JavaFileType.INSTANCE, "class A {}")

        val kotlinCompiledLightClass = myFixture.findClass("kotlinx.coroutines.Job")
        assertNotNull("kotlinx.coroutines.Job is not found", kotlinCompiledLightClass)

        assertTrue(
            "kotlinx.coroutines.Job should be accessible from Java file",
            PsiResolveHelper.getInstance(project).isAccessible(
                kotlinCompiledLightClass,
                myFixture.file,
                kotlinCompiledLightClass,
            )
        )
    }

    private object ProjectDescriptor : LightProjectDescriptor() {
        override fun setUpProject(project: Project, handler: SetupHandler) {
            super.setUpProject(project, handler)

            runWriteAction {
                val module = createModule(project, "module-with-coroutines")
                val coroutinesLibrary = TestKotlinArtifacts.kotlinxCoroutines
                PsiTestUtil.addLibrary(module, "Coroutines", coroutinesLibrary.parent.toString(), coroutinesLibrary.name)
            }
        }
    }
}