package org.jetbrains.plugins.groovy.dsl

import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessInspection

/**
 * @author peter
 */
class DsldTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return ''
  }

  public void testUnknownPointcut() {
    checkHighlighting "contribute(asdfsadf()) { property name:'foo', type:'String' }",
                      'println foo.substring(2) + <warning>bar</warning>'
  }

  public void testCurrentType() {
    checkHighlighting 'contribute(currentType("java.lang.String")) { property name:"foo" }',
                      'println "".foo + [].<warning>foo</warning>'
  }

  public void testSubType() {
    checkHighlighting 'contribute(currentType(subType("java.lang.String"))) { property name:"foo" }',
                      'println "".foo + [].<warning>foo</warning>'
  }

  public void testTypeName() {
    checkHighlighting 'contribute(currentType(name("Foo"))) { property name:"foo" }',
                      '''
class Foo {}
class Bar extends Foo {}
println new Foo().foo + new Bar().<warning>foo</warning>
'''
  }

  public void testBind() {
    checkHighlighting 'contribute(bind(types:currentType("java.lang.CharSequence"))) { property name:types[0].name[-3..-1] }',
                      'println "".ing + "".<warning>foo</warning>'
  }

  public void testImplicitBind() {
    checkHighlighting 'contribute(types:currentType("java.lang.CharSequence")) { property name:types[0].name[-3..-1] }',
                      'println "".ing + "".<warning>foo</warning>'
  }

  public void testEnclosingType() {
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

  public void testEnclosingMethod() {
    checkHighlighting 'contribute(enclosingMethod("goo")) { property name:"foo" }',
                      '''
class Foo {
  def goo() { println foo + "".foo }
  def doo() { println <warning>foo</warning> }
}
'''
  }

  public void testMethodName() {
    checkHighlighting 'contribute(enclosingMethod(name("goo"))) { property name:"foo" }',
                      '''
def goo() { println foo + "".foo }
def doo() { println <warning>foo</warning> }
'''
  }


  private def checkHighlighting(String dsl, String code) {
    def file = myFixture.addFileToProject('a.gdsl', dsl)
    GroovyDslFileIndex.activateUntilModification(file.virtualFile)

    checkHighlighting(code)
  }

  private def checkHighlighting(String text) {
    myFixture.configureByText('a.groovy', text)
    myFixture.enableInspections(new GroovyUnresolvedAccessInspection())
    myFixture.checkHighlighting(true, false, false)
  }
}
