// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.java.compiler.CompilerReferencesTestBase
import com.intellij.psi.CommonClassNames
import com.intellij.testFramework.SkipSlowTestLocally

@SkipSlowTestLocally
open class KotlinCompilerRefHelperTest : CompilerReferencesTestBase() {
    protected open val isFir: Boolean = false

    override fun setUp() {
        super.setUp()
        if (isFir) {
            project.enableK2Compiler()
        } else {
            project.enableK1Compiler()
        }
    }

    fun `test dirty scope`() {
        installCompiler()
        val javaFile = myFixture.addFileToProject(
            "JavaClass.java", """
            package one;
            import java.util.ArrayList;
            class A {
            public static void main(String[] args){
              new ArrayList<String>();
            }
            }
        """.trimIndent()
        )

        val secondJavaFile = myFixture.addFileToProject("JavaClass2.java", "")
        val kotlinFile = myFixture.addFileToProject("KotlinClass.kt", "")
        val textFile = myFixture.addFileToProject("text.txt", "")
        rebuildProject()

        fun assertFullCompilerProject() {
            val scope = findScope()
            assertFalse("file without references shouldn't be in scope", secondJavaFile.virtualFile in scope) // without reference
            assertTrue("file with references must be in scope ", javaFile.virtualFile in scope) // with reference
            assertTrue("non-java files must be presented in scope", kotlinFile.virtualFile in scope) // not java
            assertTrue("non-java files must be presented in scope", textFile.virtualFile in scope) // not java
        }

        assertFullCompilerProject()
        myFixture.renameElement(textFile, "text2.txt")
        assertFullCompilerProject()

        myFixture.renameElement(kotlinFile, "KotlinClass1.kt")
        val scope = findScope()
        assertTrue(secondJavaFile.virtualFile in scope) // dirty
        assertTrue(javaFile.virtualFile in scope) // dirty and with reference
        assertTrue("non-java files must be presented in scope", kotlinFile.virtualFile in scope) // not java and dirty
        assertTrue("non-java files must be presented in scope", textFile.virtualFile in scope) // not java and dirty
    }

    private fun findScope() = CompilerReferenceService.getInstance(project)
        .getScopeWithCodeReferences(myFixture.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST))
        ?: error("Scope not found")
}