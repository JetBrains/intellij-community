// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.i18n.TitleCapitalizationInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class KtCapitalizationInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  fun testBasic() {
    myFixture.enableInspections(TitleCapitalizationInspection())
    myFixture.configureByText("Foo.kt", """
       import org.jetbrains.annotations.*      

       class Foo {
         fun consumeTitle(@Nls(capitalization=Nls.Capitalization.Title) <warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning> : String) {}
         fun consumeSentence(@Nls(capitalization=Nls.Capitalization.Sentence) <warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning> : String) {}
      
         fun foo(@Nls(capitalization=Nls.Capitalization.Sentence) s: String) {
           val s1 = <warning descr="The string is used in both title and sentence capitalization contexts">"Hello World"</warning>
           val s2 = <warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">"Hello World"</warning>
           consumeTitle("Hello World")
           consumeTitle(<warning descr="String 'Hello world' is not properly capitalized. It should have title capitalization">"Hello world"</warning>)
           consumeTitle(<warning descr="The sentence capitalization is provided where title capitalization is required">s</warning>)
           consumeTitle(s1)
           consumeSentence(<warning descr="String 'hello world' is not properly capitalized. It should have sentence capitalization">"hello world"</warning>)
           consumeSentence(<warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">"Hello World"</warning>)
           consumeSentence("Hello world")
           consumeSentence(s)
           consumeSentence(s1)
           consumeSentence(s2)
         }
       }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testProperties() {
    val props = """
      property.lowercase=hello world
      property.titlecase=Hello World""".trimIndent()
    myFixture.addFileToProject("MyBundle.properties", props)
    myFixture.enableInspections(TitleCapitalizationInspection())
    myFixture.configureByText("Foo.kt", """
      import org.jetbrains.annotations.*

      class Foo {
        fun message(@PropertyKey(resourceBundle = "MyBundle") key : String) : String = key
        fun consumeTitle(@Nls(capitalization=Nls.Capitalization.Title) <warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning> : String) {}
        fun consumeSentence(@Nls(capitalization=Nls.Capitalization.Sentence) <warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning> : String) {}
        
        fun test() {
          consumeTitle(<warning descr="String 'hello world' is not properly capitalized. It should have title capitalization">message("property.lowercase")</warning>)
          consumeSentence(this.<warning descr="String 'hello world' is not properly capitalized. It should have sentence capitalization">message("property.lowercase")</warning>)
          consumeTitle(message("property.titlecase"))
          consumeSentence(this.<warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">message("property.titlecase")</warning>)
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }
}

