// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

abstract class ToolWindowStripeTitleInspectionTestBase : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    // DevKit recognizes a plugin project by this class, see PsiUtil.IDE_PROJECT_MARKER_CLASS.
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}")
    // DOM takes the allowed attributes from the bean. Without the annotations, it reports its own problems.
    myFixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Attribute { String value() default \"\"; }")
    myFixture.addClass(
      """
      package com.intellij.openapi.wm;
      import com.intellij.util.xmlb.annotations.Attribute;
      public class ToolWindowEP {
        @Attribute public String id;
        @Attribute public String factoryClass;
      }
      """.trimIndent()
    )
    // The inspection counts only the calls that resolve to these methods.
    myFixture.addClass(
      """
      package com.intellij.openapi.wm;
      public interface ToolWindow {
        String getStripeTitle();
        void setStripeTitle(String title);
        void setStripeTitleProvider(java.util.function.Supplier<String> title);
        void setStripeShortTitleProvider(java.util.function.Supplier<String> title);
      }
      """.trimIndent()
    )
    // An id-less descriptor declares the extension point under the 'com.intellij' prefix.
    addFile(
      "META-INF/extensionPoints.xml", """
      <idea-plugin>
        <extensionPoints>
          <extensionPoint name="toolWindow" beanClass="com.intellij.openapi.wm.ToolWindowEP"/>
        </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.enableInspections(ToolWindowStripeTitleInspection())
  }

  /** The fixture creates the JPS module that holds the descriptor, so its name is not a literal. */
  protected val moduleName: String get() = myFixture.module.name

  protected fun addFile(path: String, @Language("") text: String): VirtualFile = myFixture.addFileToProject(path, text).virtualFile

  /**
   * The inspection reads the body of the factory class. The fixture forbids the tree of any file except the checked one,
   * and it cannot allow a single file, so this allows all files.
   */
  protected fun addFactory(path: String, @Language("") text: String) {
    myFixture.allowTreeAccessForAllFiles()
    addFile(path, text)
  }
}
