// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils


class GroovyComplexArgumentLabelAnnotatorTest : LightGroovyTestCase() {
  override fun getBasePath() = "${TestUtils.getTestDataPath()}/annotator/"

  override fun getProjectDescriptor(): LightProjectDescriptor = GroovyProjectDescriptors.GROOVY_4_0

  fun `test should not highlight literals`() {
    val tripleQuote = "\"\"\""
    myFixture.configureByText("main.groovy", """
      static void main(String[] args) {  
          def a = [1: 1]
          def b = [1.0: 1]
          def c = ['a': 1]
          def d = ['aaa': 1]
          def e = ["aaa": 1]
          def f = ['''a''': 1]
          def g = [${tripleQuote}a${tripleQuote}: 1]
          def h = [0b10: 1]
          def i = [077: 1]
          def j = [0x1: 1]
          def k = [1g: 1]
          def l = [1k: 1]
          def m = [1i: 1]
          def n = [1d: 1]
          def o = [1f: 1]
      }
    """.trimIndent())
    myFixture.testHighlighting(false, false, false)
  }

  fun `test should not highlight lists and dicts`() {
    myFixture.configureByText("main.groovy", """
      static void main(String[] args) {  
          def a = [[]: 1]
          def b = [[1, 2, 3]: 1]
          def c = [[1: 1, 2: 1, 3:1]: 1]
          def d = [[[]]: 1]
          def e = [[[], [], [1, 2, 3]]: 1]
      }
    """.trimIndent())
    myFixture.testHighlighting(false, false, false)
  }

  fun `test should not highlight lambdas`() {
    myFixture.configureByText("main.groovy", """
      static void main(String[] args) {  
          def a = [{}: 1]
          def b = [{ 10 }: 1]
          def c = [{x, y -> x + y}: 1]
          def d = [{x -> 1}: 1]
          def e = [{_ -> println}: 1]
      }
    """.trimIndent())
    myFixture.testHighlighting(false, false, false)
  }

  fun `test should not highlight simple references`() {
    myFixture.configureByText("main.groovy", """
      class MyClass {
      
      }
      static void main(String[] args) {
          MyClass clazz = new MyClass()
          Integer x = 1
          Double y = 2
          def a = [x: 1]
          def b = [y: 1]
          def c = [clazz: 1]
      }
    """.trimIndent())
    myFixture.testHighlighting(false, false, false)
  }

  fun `test should not highlight complex references in parenthesis`() {
    myFixture.configureByText("main.groovy", """
      class MyClass {
          int x 
          
          static void emptyFun(){}
          
          static void nonEmptyFun(x){}
      }
      
      static void emptyFun() {}
      
      static void nonEmptyFun(x) {}
      
      static void main(String[] args) {
          MyClass clazz = new MyClass(x: 1)
          Integer x = 1
          def a = [(x?.byteValue()): 1]
          def b = [(MyClass.emptyFun()): 1]
          def c = [(MyClass.nonEmptyFun(x)): 1]
          def d = [(clazz.x): 1]
          def e = [(emptyFun()): 1]
          def f = [(nonEmptyFun(x)): 1]
          def g = [({ println }()): 1]
          def h = [({arg -> println arg}(x)): 1]
          def i = [(a[x]): 1]
      }
    """.trimIndent())
    myFixture.testHighlighting(false, false, false)
  }

  fun `test should highlight complex references`() {
    myFixture.configureByText("main.groovy", """
      class MyClass {
          int x 
          
          static void emptyFun(){}
          
          static void nonEmptyFun(x){}
      }
      
      static void emptyFun() {}
      
      static void nonEmptyFun(x) {}
      
      static void main(String[] args) {
          MyClass clazz = new MyClass(x: 1)
          Integer x = 1
          def a = [<error descr="A complex label expression before a colon must be parenthesized">x?.byteValue()</error>: 1]
          def b = [<error descr="A complex label expression before a colon must be parenthesized">MyClass.emptyFun()</error>: 1]
          def c = [<error descr="A complex label expression before a colon must be parenthesized">MyClass.nonEmptyFun(x)</error>: 1]
          def d = [<error descr="A complex label expression before a colon must be parenthesized">clazz.x</error>: 1]
          def e = [<error descr="A complex label expression before a colon must be parenthesized">emptyFun()</error>: 1]
          def f = [<error descr="A complex label expression before a colon must be parenthesized">nonEmptyFun(x)</error>: 1]
          def g = [<error descr="A complex label expression before a colon must be parenthesized">{ println }()</error>: 1]
          def h = [<error descr="A complex label expression before a colon must be parenthesized">{arg -> println arg}(x)</error>: 1]
          def i = [<error descr="A complex label expression before a colon must be parenthesized">a[x]</error>: 1]
      }
    """.trimIndent())
    myFixture.testHighlighting(false, false, false)
  }

  fun `test should highlight complex operators`() {
    myFixture.configureByText("main.groovy", """      
      static void main(String[] args) {
          Integer x = 1
          String string = null;
          def list = []
          def map = [1:1]
          def a = [<error descr="A complex label expression before a colon must be parenthesized">1 + x</error>: 1]
          def b = [<error descr="A complex label expression before a colon must be parenthesized">1 - x</error>: 1]
          def c = [<error descr="A complex label expression before a colon must be parenthesized">1 * x</error>: 1]
          def d = [<error descr="A complex label expression before a colon must be parenthesized">1 / x</error>: 1]
          def e = [<error descr="A complex label expression before a colon must be parenthesized">1 % x</error>: 1]
          def f = [<error descr="A complex label expression before a colon must be parenthesized">1 ** x</error>: 1]
          def g = [<error descr="A complex label expression before a colon must be parenthesized">x += 1</error>: 1]
          def h = [<error descr="A complex label expression before a colon must be parenthesized">x -= 1</error>: 1]
          def i = [<error descr="A complex label expression before a colon must be parenthesized">x *= 1</error>: 1]
          def j = [<error descr="A complex label expression before a colon must be parenthesized">x /= 1</error>: 1]
          def k = [<error descr="A complex label expression before a colon must be parenthesized">x %= 1</error>: 1]
          def l = [<error descr="A complex label expression before a colon must be parenthesized">x **= 1</error>: 1]
          def m = [<error descr="A complex label expression before a colon must be parenthesized">+x</error>: 1]
          def n = [<error descr="A complex label expression before a colon must be parenthesized">-x</error>: 1]
          def o = [<error descr="A complex label expression before a colon must be parenthesized">++x</error>: 1]
          def p = [<error descr="A complex label expression before a colon must be parenthesized">--x</error>: 1]
          def q = [<error descr="A complex label expression before a colon must be parenthesized">x == 1</error>: 1]
          def r = [<error descr="A complex label expression before a colon must be parenthesized">x < 1</error>: 1]
          def u = [<error descr="A complex label expression before a colon must be parenthesized">x <= 1</error>: 1]
          def w = [<error descr="A complex label expression before a colon must be parenthesized">x <= 1</error>: 1]
          def xx = [<error descr="A complex label expression before a colon must be parenthesized">x > 1</error>: 1]
          def y = [<error descr="A complex label expression before a colon must be parenthesized">x >= 1</error>: 1]
          def z = [<error descr="A complex label expression before a colon must be parenthesized">x != 1</error>: 1] 
          def aa = [<error descr="A complex label expression before a colon must be parenthesized">x === x</error>: 1] 
          def bb = [<error descr="A complex label expression before a colon must be parenthesized">x !== x</error>: 1]
          def cc = [<error descr="A complex label expression before a colon must be parenthesized">true && false</error>: 1]
          def dd = [<error descr="A complex label expression before a colon must be parenthesized">true || false</error>: 1]
          def ee = [<error descr="A complex label expression before a colon must be parenthesized">~x</error>: 1]
          def ff = [<error descr="A complex label expression before a colon must be parenthesized">x | x</error>: 1]
          def gg = [<error descr="A complex label expression before a colon must be parenthesized">x & x</error>: 1]
          def hh = [<error descr="A complex label expression before a colon must be parenthesized">x & x</error>: 1]
          def jj = [<error descr="A complex label expression before a colon must be parenthesized">x << 1</error>: 1]
          def kk = [<error descr="A complex label expression before a colon must be parenthesized">x >> 1</error>: 1]
          def ll = [<error descr="A complex label expression before a colon must be parenthesized">x >>> 1</error>: 1]
          def mm = [<error descr="A complex label expression before a colon must be parenthesized">string ? true : false</error>: 1]
          def nn = [<error descr="A complex label expression before a colon must be parenthesized">string ?: string</error>: 1]
          def oo = [<error descr="A complex label expression before a colon must be parenthesized">string ?= string</error>: 1]
          def pp = [<error descr="A complex label expression before a colon must be parenthesized">string.&length</error>: 1]
          def rr = [<error descr="A complex label expression before a colon must be parenthesized">string =~ 'foo'</error>: 1]
          def ss = [<error descr="A complex label expression before a colon must be parenthesized">string ==~ 'foo'</error>: 1]
          def vv = [<error descr="A complex label expression before a colon must be parenthesized">0..x</error>: 1]
          def ww = [<error descr="A complex label expression before a colon must be parenthesized">x <=> x</error>: 1]
          def xxx = [<error descr="A complex label expression before a colon must be parenthesized">x in list</error>: 1]
          def yy = [<error descr="A complex label expression before a colon must be parenthesized">(Double) x</error>: 1]
          def bbb = [<error descr="A complex label expression before a colon must be parenthesized">x |= x</error>: 1]
          def ccc = [<error descr="A complex label expression before a colon must be parenthesized">x &= x</error>: 1]
          def ddd = [<error descr="A complex label expression before a colon must be parenthesized">x <<= 1</error>: 1]
          def eee = [<error descr="A complex label expression before a colon must be parenthesized">x >>= 1</error>: 1]
          def fff = [<error descr="A complex label expression before a colon must be parenthesized">x >>>= 1</error>: 1]
      }
    """.trimIndent())
    myFixture.testHighlighting(false, false, false)
  }

  fun `test should not highlight complex operators in parenthesis`() {
    myFixture.configureByText("main.groovy", """      
      static void main(String[] args) {
          Integer x = 1
          String string = null;
          def list = []
          def map = [1:1]
          def a = [(1 + x): 1]
          def b = [(1 - x): 1]
          def c = [(1 * x): 1]
          def d = [(1 / x): 1]
          def e = [(1 % x): 1]
          def f = [(1 ** x): 1]
          def g = [(x += 1): 1]
          def h = [(x -= 1): 1]
          def i = [(x *= 1): 1]
          def j = [(x /= 1): 1]
          def k = [(x %= 1): 1]
          def l = [(x **= 1): 1]
          def m = [(+x): 1]
          def n = [(-x): 1]
          def o = [(++x): 1]
          def p = [(--x): 1]
          def q = [(x == 1): 1]
          def r = [(x < 1): 1]
          def u = [(x <= 1): 1]
          def w = [(x <= 1): 1]
          def xx = [(x > 1): 1]
          def y = [(x >= 1): 1]
          def z = [(x != 1): 1] 
          def aa = [(x === x): 1] 
          def bb = [(x !== x): 1]
          def cc = [(true && false): 1]
          def dd = [(true || false): 1]
          def ee = [(~x): 1]
          def ff = [(x | x): 1]
          def gg = [(x & x): 1]
          def hh = [(x & x): 1]
          def jj = [(x << 1): 1]
          def kk = [(x >> 1): 1]
          def ll = [(x >>> 1): 1]
          def mm = [(string ? true : false): 1]
          def nn = [(string ?: string): 1]
          def oo = [(string ?= string): 1]
          def pp = [(string.&length): 1]
          def rr = [(string =~ 'foo'): 1]
          def ss = [(string ==~ 'foo'): 1]
          def vv = [(0..x): 1]
          def ww = [(x <=> x): 1]
          def xxx = [(x in list): 1]
          def yy = [((Double) x): 1]
          def bbb = [(x |= x): 1]
          def ccc = [(x &= x): 1]
          def ddd = [(x <<= 1): 1]
          def eee = [(x >>= 1): 1]
          def fff = [(x >>>= 1): 1]
      }
    """.trimIndent())
    myFixture.testHighlighting(false, false, false)
  }

  fun `test quickfix`() {
    val testName = getTestName(true).trim()
    CodeInsightTestUtil.doIntentionTest(myFixture, GroovyBundle.message("groovy.complex.argument.label.quick.fix.message"), "${testName}_before.groovy", "${testName}_after.groovy")
    myFixture.availableIntentions
  }
}