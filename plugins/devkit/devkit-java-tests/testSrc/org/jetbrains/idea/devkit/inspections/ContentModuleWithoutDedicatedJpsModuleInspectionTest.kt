// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.module.JavaModuleType
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

internal class ContentModuleWithoutDedicatedJpsModuleInspectionTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ContentModuleWithoutDedicatedJpsModuleInspection::class.java)
  }

  fun `test content module without dedicated JPS module`() {
    PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "module1", myFixture.tempDirFixture.findOrCreateDir("module1"))
    PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "module2", myFixture.tempDirFixture.findOrCreateDir("module2"))
    myFixture.addFileToProject("module1/module1.xml", "<idea-plugin/>")
    myFixture.addFileToProject("module2/module2.xml", "<idea-plugin/>")
    myFixture.addFileToProject("module2/module2.sub.xml", "<idea-plugin/>")
    val pluginXml = myFixture.addFileToProject("plugin.xml", """
      |<idea-plugin>
      |  <content>
      |    <module name="module1" />    
      |    <module name="module2" />    
      |    <<warning descr="Content modules without a dedicated IntelliJ IDEA module are deprecated">module</warning> name="module2/sub" />    
      |  </content>
      |</idea-plugin>
    """.trimMargin())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }
}
