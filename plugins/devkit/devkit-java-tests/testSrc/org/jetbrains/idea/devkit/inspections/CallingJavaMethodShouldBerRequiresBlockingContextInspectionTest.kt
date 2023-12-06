// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CallingJavaMethodShouldBerRequiresBlockingContextInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  @Before
  fun addAnnotationAndEnableInspection() {
    myFixture.addClass("""
      package com.intellij.util.concurrency.annotations;
      
      
      public @interface RequiresBlockingContext {}
    """.trimIndent())

    myFixture.enableInspections(CallingMethodShouldBeRequiresBlockingContextInspection::class.java)
  }

  private val inspectionDescr = "Calling method should be annotated with '@RequiresBlockingContext'"
  private val inspectionFix = "Annotate calling method with '@RequiresBlockingContext'"

  @Test
  fun `simple call`() {
    myFixture.configureByText("Test.java", """
      import com.intellij.util.concurrency.annotations.*;
      
      class Test {
        @RequiresBlockingContext
        void a() {}
        
        void b(boolean a, boolean b) {
          if (a) {
            if (b) {
              <weak_warning descr="$inspectionDescr">a<caret></weak_warning>();
            }
          }
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(inspectionFix)
    TestCase.assertNotNull(intention)
    myFixture.checkPreviewAndLaunchAction(intention!!)

    myFixture.checkResult("""
      import com.intellij.util.concurrency.annotations.*;
      
      class Test {
        @RequiresBlockingContext
        void a() {}
        
        @RequiresBlockingContext
        void b(boolean a, boolean b) {
          if (a) {
            if (b) {
              a();
            }
          }
        }
      }
    """.trimIndent())
  }

  @Test
  fun `call as parameter`() {
    myFixture.configureByText("Test.java", """
      import com.intellij.util.concurrency.annotations.*;
      
      class Test {
        @RequiresBlockingContext
        int a() {
          return 10;
        }
        
        void c(int a) {}
        
        void b() {
          c(<weak_warning descr="$inspectionDescr">a<caret></weak_warning>());
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()

    val intention = myFixture.getAvailableIntention(inspectionFix)
    TestCase.assertNotNull(intention)
    myFixture.checkPreviewAndLaunchAction(intention!!)

    myFixture.checkResult("""
      import com.intellij.util.concurrency.annotations.*;
      
      class Test {
        @RequiresBlockingContext
        int a() {
          return 10;
        }
        
        void c(int a) {}
        
        @RequiresBlockingContext
        void b() {
          c(a());
        }
      }
    """.trimIndent())
  }

  @Test
  fun `no inspection in annotated method`() {
    myFixture.configureByText("Test.java", """
      import com.intellij.util.concurrency.annotations.*;
      
      class Test {
        @RequiresBlockingContext
        void a() {}
        
        @RequiresBlockingContext
        void b() {
          a();
        }
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }

  @Test
  fun `no inspection in annotated by super method`() {
    myFixture.configureByText("Test.java", """
      import com.intellij.util.concurrency.annotations.*;
      
      class Test {
        interface A {
          @RequiresBlockingContext
          void a();
        }
        
        class B implements A {
          @RequiresBlockingContext
          void b() {}
         
          @Override
          public void a() {
            b();
          }
        }
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }

  @Test
  fun `no inspection in lambda`() {
    myFixture.configureByText("Test.java", """
      import com.intellij.util.concurrency.annotations.*;
      
      class Test {
        void accept(Runnable r) {}
        
        @RequiresBlockingContext
        void a() {}
        
        void b() {
          accept(() -> {
            a();
          });
        }
      }
    """.trimIndent())

    myFixture.testHighlighting()
  }
}