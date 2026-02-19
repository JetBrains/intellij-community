// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiReference
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class GroovyMultiModuleResolveTest : JavaCodeInsightFixtureTestCase() {
  fun `test same class from different modules`() {
    val groovyModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "groovyModule", myFixture.tempDirFixture.findOrCreateDir("groovyModule"))
    myFixture.addFileToProject("groovyModule/foo/Foo.groovy", """
      package foo
      
      class Foo {}
    """.trimIndent())
    ModuleRootModificationUtil.addDependency(myFixture.module, groovyModule)

    val javaModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "javaModule", myFixture.tempDirFixture.findOrCreateDir("javaModule"))
    myFixture.addFileToProject("javaModule/foo/Foo.java", """
      package foo;
      
      public class Foo {}
    """.trimIndent())
    ModuleRootModificationUtil.addDependency(myFixture.module, javaModule)

    myFixture.configureByText("Main.groovy", """
      import foo.Foo
      
      class Main {
        F<caret>oo foo = new Foo()
      }
    """.trimIndent())
    val resolved = (myFixture.file.findElementAt(myFixture.caretOffset)?.parent as? PsiReference)?.resolve()

    // resolve to Groovy because dependency was added first and has higher priority
    assertEquals("Foo.groovy", resolved?.containingFile?.name)
  }
}