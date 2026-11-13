// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiReference
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.util.configureByExactName
import org.jetbrains.plugins.groovy.util.groovyFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class GroovyMultiModuleResolveTest {
  // The test adds modules, so each test gets its own project.
  private val myFixture by groovyFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)

  @Test
  fun `test same class from different modules`() {
    val project = myFixture.project
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

    configureByExactName(myFixture, "Main.groovy", """
      import foo.Foo

      class Main {
        F<caret>oo foo = new Foo()
      }
    """.trimIndent())
    val resolvedFileName = ReadAction.computeBlocking<String?, Throwable> {
      (myFixture.file.findElementAt(myFixture.caretOffset)?.parent as? PsiReference)?.resolve()?.containingFile?.name
    }

    // resolve to Groovy because dependency was added first and has higher priority
    assertEquals("Foo.groovy", resolvedFileName)
  }
}
