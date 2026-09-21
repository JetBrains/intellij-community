// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.UnstableApiUsageInspection
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.util.PsiUtil

private const val WARNING = "JetBrains Marketplace does not publish a plugin version that uses JetBrains internal APIs. " +
                            "Such APIs are not allowed to use in plugins."

internal class JetBrainsInternalApiUsageInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    // it is required for correct recognizing if the project is a plugin project (see PsiUtil.IDE_PROJECT_MARKER_CLASS):
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}")
    myFixture.addClass("""
      package org.jetbrains.annotations;

      public final class ApiStatus {
        public @interface Internal { }
        public @interface Experimental { }
      }
    """.trimIndent())
    myFixture.addClass("""
      package internal.pkg;

      import org.jetbrains.annotations.ApiStatus;

      @ApiStatus.Internal
      public class AnnotatedClass {
        public void nonAnnotatedMethodInAnnotatedClass() { }
      }
    """.trimIndent())
    myFixture.addClass("""
      package internal.pkg;

      import org.jetbrains.annotations.ApiStatus;

      public class NonAnnotatedClass {
        @ApiStatus.Internal public void annotatedMethodInNonAnnotatedClass() { }
        @ApiStatus.Experimental public void experimentalMethodInNonAnnotatedClass() { }
      }
    """.trimIndent())
  }

  fun `test the module is a plugin module`() {
    assertTrue(PsiUtil.isPluginModule(module))
  }

  fun `test usage of a JetBrains internal api is an error`() {
    myFixture.enableInspections(JetBrainsInternalApiUsageInspection())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Usage {
        void test(internal.pkg.NonAnnotatedClass c) {
          c.<error descr="'annotatedMethodInNonAnnotatedClass()' is marked internal with @ApiStatus.Internal. $WARNING">annotatedMethodInNonAnnotatedClass</error>();
          c.experimentalMethodInNonAnnotatedClass();
          internal.pkg.<error descr="'internal.pkg.AnnotatedClass' is marked internal with @ApiStatus.Internal. $WARNING">AnnotatedClass</error> a = null;
          a.<error descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in JetBrains internal class 'internal.pkg.AnnotatedClass' marked with @ApiStatus.Internal. $WARNING">nonAnnotatedMethodInAnnotatedClass</error>();
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun `test the inspection reports nothing in the IntelliJ Platform project`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    try {
      myFixture.enableInspections(JetBrainsInternalApiUsageInspection())
      myFixture.configureByText(JavaFileType.INSTANCE, """
        class Usage {
          void test(internal.pkg.NonAnnotatedClass c) {
            c.annotatedMethodInNonAnnotatedClass();
          }
        }
      """.trimIndent())
      myFixture.checkHighlighting()
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    }
  }

  fun `test the standard inspection skips the internal api in a plugin module and keeps the other reports`() {
    myFixture.enableInspections(UnstableApiUsageInspection())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Usage {
        void test(internal.pkg.NonAnnotatedClass c) {
          c.annotatedMethodInNonAnnotatedClass();
          c.<warning descr="'experimentalMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">experimentalMethodInNonAnnotatedClass</warning>();
          internal.pkg.AnnotatedClass a = null;
          a.nonAnnotatedMethodInAnnotatedClass();
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}
