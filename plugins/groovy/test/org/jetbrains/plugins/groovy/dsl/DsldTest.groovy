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

  public void testBind() {
    checkHighlighting 'contribute(bind(types:currentType("java.lang.CharSequence"))) { property name:types[0].name[-3..-1] }',
                      'println "".ing + "".<warning>foo</warning>'
  }

  public void testImplicitBind() {
    checkHighlighting 'contribute(types:currentType("java.lang.CharSequence")) { property name:types[0].name[-3..-1] }',
                      'println "".ing + "".<warning>foo</warning>'
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
