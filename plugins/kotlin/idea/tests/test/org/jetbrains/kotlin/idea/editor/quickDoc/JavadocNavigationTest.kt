// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor.quickDoc

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.test.TestRoot
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/kdoc/javadoc/navigate")
@RunWith(JUnit38ClassRunner::class)
class JavadocNavigationTest() : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.INSTANCE

    fun testExtMethod() {
        myFixture.addFileToProject(
            "Project.kt", """public interface Project {
  fun guessDir() = false
}"""
        )
        val file = myFixture.configureByFile(getTestName(false) + ".java")
        UsefulTestCase.assertInstanceOf(file, PsiJavaFile::class.java)
        val psiClass = (file as PsiJavaFile).classes[0]
        val docInfo = JavaDocInfoGenerator(project, psiClass).generateDocInfo(emptyList())
        Assert.assertEquals(
            """<div class='definition'><pre><span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">ExtMethod</span>
<span style="color:#000080;font-weight:bold;">extends</span> <a href="psi_element://Super"><code><span style="color:#000000;">Super</span></code></a></pre></div><div class='content'>
  <a href="psi_element://Project#guessDir()"><code>directory</code></a>
 </div><table class='sections'><p></table>""", docInfo)
    }
    
}
