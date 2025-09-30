// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ide

import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class Groovy50InstanceMainRunLineMarkerTest : GroovyMainRunLineMarkerTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  fun testVoidMain() {
    fixture.configureByText("Main.groovy", """
       void main<caret>() {}
     """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testVoidMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
       void main<caret>(args) {}
     """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testVoidMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
       void main<caret>(Object args) {}
    """)
    doRunLineMarkerTest()
  }

  fun testVoidMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
       void main<caret>(String[] args) {}
     """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testVoidMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      void main<caret>(String[] args, something) {}
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testDefMain() {
    fixture.configureByText("Main.groovy", """
       def main<caret>() {}
    """)
    doRunLineMarkerTest()
  }

  fun testDefMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
       def main<caret>(args) {}
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testDefMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
       def main<caret>(Object args) {}
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testDefMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
       def main<caret>(String[] args) {}
    """)
    doRunLineMarkerTest()
  }

  fun testDefMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      void main<caret>(String[] args, something) {}
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticMain() {
    fixture.configureByText("Main.groovy", """
       static main<caret>() {}
    """)
    doRunLineMarkerTest()
  }

  fun testStaticMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
       static main<caret>(args) {}
    """)
    doRunLineMarkerTest()
  }

  fun testStaticMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
       static main<caret>(Object args) {}
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testStaticMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
       static main<caret>(String[] args) {}
    """)
    doRunLineMarkerTest()
  }


  fun testStaticMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      static main<caret>(String[] args, something) {}
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticVoidMain() {
    fixture.configureByText("Main.groovy", """
       static void main<caret>() {}
    """)
    doRunLineMarkerTest()
  }

  fun testStaticVoidMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
       static void main<caret>(args) {}
    """)
    doRunLineMarkerTest()
  }

  fun testStaticVoidMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
       static void main<caret>(Object args) {}
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testStaticVoidMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
       static void main<caret>(String[] args) {}
    """)
    doRunLineMarkerTest()
  }

  fun testStaticVoidMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      static void main<caret>(String[] args, something) {}
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticDefMain() {
    fixture.configureByText("Main.groovy", """
       static def main<caret>() {}
    """)
    doRunLineMarkerTest()
  }

  fun testStaticDefMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
       static def main<caret>(args) {}
    """)
    doRunLineMarkerTest()
  }

  fun testStaticDefMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
       static def main<caret>(Object args) {}
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testStaticDefMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
       static def main<caret>(String[] args) {}
    """)
    doRunLineMarkerTest()
  }


  fun testStaticDefMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      static def main<caret>(String[] args, something) {}
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticWithStringArgsHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      static void main(String[] args) {}
      void main<caret>(String[] args) {}
    """)
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testWithStringArgsHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      void main(String[] args) {}
      static void main<caret>(args) {}
    """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testStaticWithUntypedArgsHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      static void main(args) {}
      void main<caret>(args) {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testWithUntypedArgsHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      void main(args) {}
      static void main<caret>() {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testStaticWithUntypedParametersHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      static void main() {}
      void main<caret>() {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }


  fun testStaticWithParameterHasHigherPriority() {
      myFixture.configureByText("MainTest.groovy", """
      static void main<caret>() {}
      static void main(String[] args) {}
      
      """.trimIndent())
      val marks = myFixture.findAllGutters()
      TestCase.assertEquals(1, marks.size)
      assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testWithParameterHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      void main<caret>() {}
      void main(String[] args) {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testInstanceWithTypedParametersHasHigherPriorityThanStatic() {
    myFixture.configureByText("MainTest.groovy", """
      static void main<caret>() {}
      void main(String[] args) {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testInstanceWithUnTypedParametersHasLowerPriorityThanStatic() {
    myFixture.configureByText("MainTest.groovy", """
      static void main<caret>() {}
      void main(args) {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testStaticWithUntypedParametersHasHigherPriorityThanInstance() {
    myFixture.configureByText("MainTest.groovy", """
      static void main(args) {}
      void main<caret>() {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }

  fun testStaticWithTypedParametersHasHigherPriorityThanInstance() {
    myFixture.configureByText("MainTest.groovy", """
      static void main(String[] args) {}
      void main<caret>() {}
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }
}