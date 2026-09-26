// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import org.jetbrains.idea.devkit.kotlin.inspections.KotlinApplicableInspectionBaseSimpleCallExpressionInspection
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class KotlinApplicableInspectionBaseSimpleCallExpressionInspectionTest : LightDevKitInspectionFixTestBase() {

  override fun getFileExtension(): String = "kt"

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(KotlinApplicableInspectionBaseSimpleCallExpressionInspection())
    addKotlinApplicableInspectionBaseStubs()
  }

  fun testHighlightsSimpleCallExpressionInspectionWithoutRangeOverride() {
    myFixture.configureByText("Test.kt", """
      package test

      import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
      import org.jetbrains.kotlin.psi.KtCallExpression

      internal class <warning descr="Override 'getApplicableRanges' to highlight only the callee expression">MyInspection</warning> : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testDoesNotHighlightWhenRangeOverrideExists() {
    myFixture.configureByText("Test.kt", """
      package test

      import com.intellij.openapi.util.TextRange
      import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
      import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
      import org.jetbrains.kotlin.psi.KtCallExpression
     
      internal class MyInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {
        override fun getApplicableRanges(element: KtCallExpression): List<TextRange> {
          return ApplicabilityRanges.calleeExpression(element)
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testDoesNotHighlightOtherSimpleSpecialization() {
    myFixture.configureByText("Test.kt", """
      package test

      import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
      import org.jetbrains.kotlin.psi.KtBinaryExpression
      
      internal class MyInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun testQuickFixAddsRangeOverride() {
    myFixture.configureByText("Test.kt", """
      package test

      import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
      import org.jetbrains.kotlin.psi.KtCallExpression
      
      internal class <caret>MyInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {
      }
    """.trimIndent())

    myFixture.launchAction(myFixture.findSingleIntention("Add 'getApplicableRanges' override"))

    myFixture.checkResult("""
      package test

      import com.intellij.openapi.util.TextRange
      import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
      import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
      import org.jetbrains.kotlin.psi.KtCallExpression
      
      internal class MyInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {
          override fun getApplicableRanges(element: KtCallExpression): List<TextRange> {
              return ApplicabilityRanges.calleeExpression(element)
          }
      }
    """.trimIndent())
  }

  private fun addKotlinApplicableInspectionBaseStubs() {
    myFixture.addClass("""
      package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections;

      import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges;
      import org.jetbrains.kotlin.psi.KtElement;

      public abstract class KotlinApplicableInspectionBase<E extends KtElement, C> {
        public @org.jetbrains.annotations.NotNull java.util.List<com.intellij.openapi.util.TextRange> getApplicableRanges(E element) { return null; }

        public abstract static class Simple<E extends KtElement, C> extends KotlinApplicableInspectionBase<E, C> { }
      }
    """.trimIndent())
    myFixture.addClass("""
     package org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators;
     public class ApplicabilityRanges {
         public static @org.jetbrains.annotations.NotNull java.util.List<com.intellij.openapi.util.TextRange> calleeExpression(org.jetbrains.kotlin.psi.KtElement element) { return null; }
      }
    """.trimMargin())
    myFixture.addClass("""package com.intellij.openapi.util;
      public class TextRange {}
    """.trimMargin())
    myFixture.addClass( """
      package org.jetbrains.kotlin.psi;
      public class KtCallExpression extends KtElement { }
    """.trimIndent())
    myFixture.addClass( """
      package org.jetbrains.kotlin.psi;
      public class KtBinaryExpression extends KtElement { }
    """.trimIndent())
    myFixture.addClass( """
      package org.jetbrains.kotlin.psi;
      public class KtElement { }
    """.trimIndent())
  }
}
