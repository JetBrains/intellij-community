// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ide

import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class GroovyPre50InstanceMainRunLineMarkerTest : GroovyMainRunLineMarkerTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_4_0
  }

  fun testVoidMain() {
    fixture.configureByText("Main.groovy", """
       void main<caret>() {}
     """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testVoidMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
       void main<caret>(args) {}
     """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testVoidMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
       void main<caret>(Object args) {}
    """)
    doAntiRunLineMarkerTest()
  }

  fun testVoidMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
       void main<caret>(String[] args) {}
     """.trimIndent())
    doAntiRunLineMarkerTest()
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
    doAntiRunLineMarkerTest()
  }

  fun testDefMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
       def main<caret>(args) {}
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testDefMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
       def main<caret>(Object args) {}
    """)
    doAntiRunLineMarkerTest()
  }

  fun testDefMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
       def main<caret>(String[] args) {}
    """)
    doAntiRunLineMarkerTest()
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
    doAntiRunLineMarkerTest()
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
    doAntiRunLineMarkerTest()
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
    doAntiRunLineMarkerTest()
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

  fun testStaticWithParameterHasHigherPriority() {
    myFixture.configureByText("MainTest.groovy", """
      static void main<caret>() {}
      static void main(String[] args) {}
      
      """.trimIndent())
    val marks = myFixture.findAllGutters()
    TestCase.assertEquals(1, marks.size)
    assertEmpty(myFixture.findGuttersAtCaret())
  }
}