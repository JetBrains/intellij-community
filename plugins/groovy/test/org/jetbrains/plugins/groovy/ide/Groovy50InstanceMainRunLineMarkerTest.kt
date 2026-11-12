// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ide

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class Groovy50InstanceMainRunLineMarkerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val myFixture by lightGroovyFixture(projectFixture)

  @Test
  fun testVoidMain() {
    myFixture.configureByText("Main.groovy", """
       void main<caret>() {}
     """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testVoidMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
       void main<caret>(args) {}
     """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testVoidMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
       void main<caret>(Object args) {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testVoidMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
       void main<caret>(String[] args) {}
     """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testVoidMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      void main<caret>(String[] args, something) {}
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testDefMain() {
    myFixture.configureByText("Main.groovy", """
       def main<caret>() {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testDefMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
       def main<caret>(args) {}
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testDefMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
       def main<caret>(Object args) {}
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testDefMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
       def main<caret>(String[] args) {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testDefMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      void main<caret>(String[] args, something) {}
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticMain() {
    myFixture.configureByText("Main.groovy", """
       static main<caret>() {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
       static main<caret>(args) {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
       static main<caret>(Object args) {}
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
       static main<caret>(String[] args) {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      static main<caret>(String[] args, something) {}
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMain() {
    myFixture.configureByText("Main.groovy", """
       static void main<caret>() {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
       static void main<caret>(args) {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
       static void main<caret>(Object args) {}
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
       static void main<caret>(String[] args) {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      static void main<caret>(String[] args, something) {}
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMain() {
    myFixture.configureByText("Main.groovy", """
       static def main<caret>() {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
       static def main<caret>(args) {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
       static def main<caret>(Object args) {}
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
       static def main<caret>(String[] args) {}
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      static def main<caret>(String[] args, something) {}
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticWithStringArgsHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      static void main(String[] args) {}
      void main<caret>(String[] args) {}
    """)
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testWithStringArgsHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      void main(String[] args) {}
      static void main<caret>(args) {}
    """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testStaticWithUntypedArgsHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      static void main(args) {}
      void main<caret>(args) {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testWithUntypedArgsHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      void main(args) {}
      static void main<caret>() {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testStaticWithUntypedParametersHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      static void main() {}
      void main<caret>() {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testStaticWithParameterHasHigherPriority() {
      myFixture.configureByText("MainTest.groovy", """
      static void main<caret>() {}
      static void main(String[] args) {}
      
      """.trimIndent())
      val marks = myFixture.findAllGutters()
      assertEquals(1, marks.size)
      assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testWithParameterHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      void main<caret>() {}
      void main(String[] args) {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testInstanceWithTypedParametersHasHigherPriorityThanStatic() {
    myFixture.configureByText("MainTest.groovy", """
      static void main<caret>() {}
      void main(String[] args) {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testInstanceWithUnTypedParametersHasLowerPriorityThanStatic() {
    myFixture.configureByText("MainTest.groovy", """
      static void main<caret>() {}
      void main(args) {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testStaticWithUntypedParametersHasHigherPriorityThanInstance() {
    myFixture.configureByText("MainTest.groovy", """
      static void main(args) {}
      void main<caret>() {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }

  @Test
  fun testStaticWithTypedParametersHasHigherPriorityThanInstance() {
    myFixture.configureByText("MainTest.groovy", """
      static void main(String[] args) {}
      void main<caret>() {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    assertEquals(1, marks.size)
    assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty()
  }
}