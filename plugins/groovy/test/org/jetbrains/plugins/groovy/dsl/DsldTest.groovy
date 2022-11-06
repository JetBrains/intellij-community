// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression

class DsldTest extends LightGroovyTestCase {

  void testUnknownPointcut() {
    checkHighlighting "contribute(asdfsadf()) { property name:'foo', type:'String' }",
                      'println foo.substring(2) + <warning>bar</warning>'
  }

  void testCurrentType() {
    checkHighlighting 'contribute(currentType("java.lang.String")) { property name:"foo" }',
                      'println "".foo + [].<warning>foo</warning>'
  }

  void testSubType() {
    checkHighlighting 'contribute(currentType(subType("java.lang.String"))) { property name:"foo" }',
                      'println "".foo + [].<warning>foo</warning>'
  }

  void testAnd() {
    checkHighlighting 'contribute(currentType(subType("java.lang.Runnable") & name("Foo"))) { property name:"foo" }',
                      '''
class Foo implements Runnable {
  void run() { println foo }
}
class Bar implements Runnable {
  void run() { println <warning>foo</warning> }
  class Foo {
   void run() { println <warning>foo</warning> }
  }
}
println <warning>foo</warning>
'''
  }

  void testOr() {
    checkHighlighting 'contribute(currentType(subType("MyRunnable") | name("Foo"))) { property name:"foo" }',
                      '''
interface MyRunnable {}
class Foo implements MyRunnable {
 void run() { println foo }
}
class Bar implements MyRunnable {
 void run() { println foo }
 static class Foo {
   void run() { println foo }
 }
}
println <warning>foo</warning>
'''
  }

  void testNot() {
    checkHighlighting 'contribute(currentType(~name("Foo"))) { property name:"foo" }',
                      '''
class Foo {
 void run() { println <warning>foo</warning> }
}
class Bar extends Foo {
 void run() { println foo }
}
'''
  }

  void testTypeName() {
    checkHighlighting 'contribute(currentType(name("Foo"))) { property name:"foo" }',
                      '''
class Foo {}
class Bar extends Foo {}
println new Foo().foo + new Bar().<warning>foo</warning>
'''
  }

  void testBind() {
    checkHighlighting 'contribute(bind(types:currentType("java.lang.CharSequence"))) { property name:types[0].name[-3..-1] }',
                      'println "".ing + "".<warning>foo</warning>'
  }

  void testImplicitBind() {
    checkHighlighting 'contribute(types:currentType("java.lang.CharSequence")) { property name:types[0].name[-3..-1] }',
                      'println "".ing + "".<warning>foo</warning>'
  }

  void testEnclosingType() {
    checkHighlighting 'contribute(enclosingType("Foo")) { property name:"foo" }',
                      '''
class Foo {
  def goo() { println foo + "".foo }
}
class Bar extends Foo {
  def goo() { println <warning>foo</warning> }
}
println new Foo().<warning>foo</warning>
println <warning>foo</warning>
'''
  }

  void testEnclosingMethod() {
    checkHighlighting 'contribute(enclosingMethod("goo")) { property name:"foo" }',
                      '''
class Foo {
  def goo() { println foo + "".foo }
  def doo() { println <warning>foo</warning> }
}
'''
  }

  void testMethodName() {
    checkHighlighting 'contribute(enclosingMethod(name("goo"))) { property name:"foo" }',
                      '''
def goo() { println foo + "".foo }
def doo() { println <warning>foo</warning> }
'''
  }

  void testSupportsVersion() {
    checkHighlighting '''
if (supportsVersion(intellij:'9.0')) {
  contribute(currentType("java.lang.String")) { property name:"foo" }
} else {
  contribute(currentType("java.lang.String")) { property name:"bar" }
}

if (!supportsVersion(groovyEclipse:'9.0')) {
  contribute(currentType("java.lang.String")) { property name:"goo" }
}
''',
                      'println "".foo + "".<warning>bar</warning> + "".goo'
  }

  void testAssertVersion() {
    checkHighlighting '''
assertVersion dsl:'1.0'
contribute(currentType("java.lang.String")) { property name:"foo" }
''',
                      'println "".foo'
  }

  void testAssertVersionDsl() {
    checkHighlighting '''
assertVersion intellij:'9.0'
contribute(currentType("java.lang.String")) { property name:"foo" }
''',
                      'println "".foo'
  }

  void testAssertVersionFail() {
    checkHighlighting '''
assertVersion intellij:'23942.0'
contribute(currentType("java.lang.String")) { property name:"foo" }
''',
                      'println "".<warning>foo</warning>'
  }

  void testAssertVersionFailDsl() {
    checkHighlighting '''
assertVersion dsl:'239.0'
contribute(currentType("java.lang.String")) { property name:"foo" }
''',
                      'println "".<warning>foo</warning>'
  }

  void testAddConstructor() {
    addDsld 'contribute(currentType("java.lang.String")) { constructor params:[foo:Integer, bar:Integer, goo:Integer] }'

    myFixture.configureByText('a.groovy', 'new Stri<caret>ng(2,3,9)')
    def newExpr = PsiTreeUtil.findElementOfClassAtOffset(myFixture.file, myFixture.editor.caretModel.offset, GrNewExpression, false)
    def method = newExpr.resolveMethod()
    assert method.constructor
    assert method.parameterList.parameters.length == 3
  }

  void testMeta() {
    def dslUrl = GdslScriptProvider.classLoader.getResource("standardDsls/metaDsl.gdsl")
    def dslVirtualFile = myFixture.copyFileToProject(dslUrl.getPath(), 'metaDsl.gdsl')
    myFixture.configureFromExistingVirtualFile(dslVirtualFile)
    GroovyDslFileIndex.activate(dslVirtualFile)
    myFixture.enableInspections(new GrUnresolvedAccessInspection())
    myFixture.checkHighlighting()
  }

  private def checkHighlighting(String dsl, String code) {
    addDsld(dsl)

    checkHighlighting(code)
  }

  private def addDsld(String dsl) {
    def file = myFixture.addFileToProject('a.gdsl', dsl)
    GroovyDslFileIndex.activate(file.virtualFile)
  }

  private def checkHighlighting(String text) {
    myFixture.configureByText('a.groovy', text)
    myFixture.enableInspections(new GrUnresolvedAccessInspection())
    myFixture.checkHighlighting(true, false, false)
  }
}
