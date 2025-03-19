// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.inspections.UElementAsPsiInspection

class KtUElementAsPsiInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()

    myFixture.addClass("package org.jetbrains.uast; public interface UElement {}")
    myFixture.addClass("""package com.intellij.psi; public interface PsiElement {
      | PsiElement getParent();
      |}""".trimMargin())
    myFixture.addClass("package com.intellij.psi; public interface PsiClass extends PsiElement {}")
    myFixture.addClass("""package org.jetbrains.uast; public interface UClass extends UElement, com.intellij.psi.PsiClass {
      | void uClassMethod();
      |}""".trimMargin())

    myFixture.enableInspections(UElementAsPsiInspection())
  }

  fun testPassAsPsiClass() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import org.jetbrains.uast.UClass
      import com.intellij.psi.PsiClass

      class UastUsage {

          fun processUClassCaller(uClass: UClass) {
             processUClass(uClass)
          }

          fun processUClass(uClass: UClass) { processPsiClass(<warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>) }

          fun processPsiClass(psiClass: PsiClass) { psiClass.toString() }

      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")

  }

  fun testExtensionMethods() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import org.jetbrains.uast.*
      import com.intellij.psi.*

      class UastUsage {

          fun processUClassCaller(psiElement: PsiElement, uClass: UClass){
             this.process(psiElement, uClass)
             uClass.process(<warning>uClass</warning>, uClass)
          }

          fun Any.process(psiClass: PsiElement, uElement: UElement?){ psiClass.toString(); uElement.toString() }

      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")

  }

  fun testAssignment() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import org.jetbrains.uast.UClass
      import com.intellij.psi.PsiClass

      class UastUsage {

          var psiClass:PsiClass

          constructor(uClass: UClass){
             psiClass = <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>
             val localPsiClass:PsiClass = <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>
             localPsiClass.toString()
          }

      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")

  }

  fun testCast() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import org.jetbrains.uast.*
      import com.intellij.psi.PsiClass

      class UastUsage {

          constructor(uElement: UElement){
             (<warning descr="Usage of UElement as PsiElement is not recommended">uElement</warning> as? PsiClass).toString()
             (uElement as? UClass).toString()
          }

      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")

  }

  fun testCallParentMethod() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import org.jetbrains.uast.UClass
      import com.intellij.psi.PsiClass

      class UastUsage {

          fun foo(uClass: UClass){
             uClass.<warning descr="Usage of UElement as PsiElement is not recommended">getParent()</warning>
             uClass.uClassMethod()
          }

      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")

  }

  fun testImplementAndCallParentMethod() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import org.jetbrains.uast.UClass
      import com.intellij.psi.*

      class UastUsage {

          fun foo(){
            val impl = UClassImpl()
            impl.<warning descr="Usage of UElement as PsiElement is not recommended">getParent()</warning>
            impl.uClassMethod()
          }

      }

      class UClassImpl: UClass {
        override fun getParent():PsiClass? = null
        override fun uClassMethod() {}
      }

      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")

  }

  fun testPsiElementExtensionFunction() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import com.intellij.psi.PsiElement
      import org.jetbrains.uast.UClass
      import org.jetbrains.uast.UElement

      class UastUsage {
        fun foo(uClass: UClass) {
          <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>.testPsiElement()
          uClass.testUElement() // no false positive
        }

        private fun PsiElement.testPsiElement() {}
        private fun UElement.testUElement() {}
      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")
  }

  fun testPsiElementExtensionFunctionInClassExtendingUElement() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import com.intellij.psi.PsiElement
      import org.jetbrains.uast.UClass
      import org.jetbrains.uast.UElement

      class TestUElement : UElement {
        fun foo(uClass: UClass) {
          <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>.testPsiElement()
          uClass.testUElement() // no false positive
        }

        private fun PsiElement.testPsiElement() {}
        private fun UElement.testUElement() {}
      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")
  }

  fun testTopLevelPsiElementExtensionFunction() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import com.intellij.psi.PsiElement
      import org.jetbrains.uast.UClass
      import org.jetbrains.uast.UElement

      class UastUsage {
        fun foo(uClass: UClass) {
          <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>.testPsiElement()
          uClass.testUElement() // no false positive
        }
      }

      private fun PsiElement.testPsiElement() {}
      private fun UElement.testUElement() {}
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")
  }

  // there was a bug reporting warning for nested methods multiple times (during analyzing top-level method and all nested)
  fun testNestedMethods() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import com.intellij.psi.PsiElement
      import org.jetbrains.uast.UClass
      import org.jetbrains.uast.UElement

      class UastUsage {
        fun function(uClass: UClass) {
          object : Runnable {
            override fun run() {
              object : Runnable {
                override fun run() {
                  object : Runnable {
                    override fun run() {
                      uClass.<warning descr="Usage of UElement as PsiElement is not recommended">parent</warning>
                    }
                  }
                }
              }
            }
          }
        }
      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")
  }

  fun testNestedKotlinFunction() {
    myFixture.configureByText("UastUsage.kt",
      //language=kotlin
      """
      import com.intellij.psi.PsiElement
      import org.jetbrains.uast.UClass
      import org.jetbrains.uast.UElement

      class UastUsage {
        fun foo(uClass: UClass) {
          fun nested() {
            <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>.testPsiElement()
          }
          nested()
        }
      }

      private fun PsiElement.testPsiElement() {}
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.kt")
  }
}
