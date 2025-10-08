// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ide

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class GroovyPre50ClassMainRunLineMarkerTest: GroovyMainRunLineMarkerTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_4_0
  }

  fun testVoidMain() {
    fixture.configureByText("Main.groovy", """
      class A {
        void main<caret>() {}
      }
     """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testVoidMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(args) {}
      }
     """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testVoidMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(Object args) {}
      }
    """)
    doAntiRunLineMarkerTest()
  }

  fun testVoidMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(String[] args) {}
      }
     """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testVoidMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testDefMain() {
    fixture.configureByText("Main.groovy", """
      class A {
        def main<caret>() {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testDefMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        def main<caret>(args) {}
      }
     """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testDefMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        def main<caret>(Object args) {}
      }
     """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testDefMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        def main<caret>(String[] args) {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testDefMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      class A {
        void main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticMain() {
    fixture.configureByText("Main.groovy", """
      class A {
        static main<caret>() {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static main<caret>(args) {}
      }
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testStaticMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static main<caret>(Object args) {}
      }
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testStaticMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static main<caret>(String[] args) {}
      }
    """.trimIndent())
    doRunLineMarkerTest()
  }


  fun testStaticMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      class A {
        static main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticVoidMain() {
    fixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>() {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticVoidMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>(args) {}
      }
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testStaticVoidMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>(Object args) {}
      }
    """)
    doRunLineMarkerTest()
  }

  fun testStaticVoidMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>(String[] args) {}
      }
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testStaticVoidMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      class A {
        static void main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticDefMain() {
    fixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>() {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }

  fun testStaticDefMainWithUntypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>(args) {}
      }
    """.trimIndent())
    doRunLineMarkerTest()
  }

  fun testStaticDefMainWithObjectArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>(Object args) {}
      }
    """)
    doRunLineMarkerTest()
  }

  fun testStaticDefMainWithTypedArgs() {
    fixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>(String[] args) {}
      }
    """.trimIndent())
    doRunLineMarkerTest()
  }


  fun testStaticDefMainMultipleArguments() {
    fixture.configureByText("Main.groovy", """
      class A {
        static def main<caret>(String[] args, something) {}
      }
    """.trimIndent())
    doAntiRunLineMarkerTest()
  }
}