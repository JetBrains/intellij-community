// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ide

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.junit.jupiter.api.Test

@TestApplication
class GroovyPre50ClassMainRunLineMarkerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0)
  }

  private val myFixture by lightGroovyFixture(projectFixture)

  @Test
  fun testVoidMain() {
    myFixture.configureByText("Main.groovy", """
      class A {
        void main<caret>() {}
      }
     """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testVoidMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(args) {}
      }
     """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testVoidMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(Object args) {}
      }
    """)
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testVoidMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(String[] args) {}
      }
     """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testVoidMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testDefMain() {
    myFixture.configureByText("Main.groovy", """
      class A {
        def main<caret>() {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testDefMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        def main<caret>(args) {}
      }
     """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testDefMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        def main<caret>(Object args) {}
      }
     """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testDefMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        def main<caret>(String[] args) {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testDefMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticMain() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static main<caret>() {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static main<caret>(args) {}
      }
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static main<caret>(Object args) {}
      }
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static main<caret>(String[] args) {}
      }
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMain() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>() {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>(args) {}
      }
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>(Object args) {}
      }
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>(String[] args) {}
      }
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticVoidMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMain() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>() {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMainWithUntypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>(args) {}
      }
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMainWithObjectArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>(Object args) {}
      }
    """)
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMainWithTypedArgs() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>(String[] args) {}
      }
    """.trimIndent())
    myFixture.doRunLineMarkerTest()
  }

  @Test
  fun testStaticDefMainMultipleArguments() {
    myFixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    myFixture.doAntiRunLineMarkerTest()
  }
}