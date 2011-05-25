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
    def file = myFixture.addFileToProject('a.gdsl', "contribute(asdfsadf()) { property name:'foo', type:'String' }")
    GroovyDslFileIndex.activateUntilModification(file.virtualFile)

    checkHighlighting('println foo.substring(2) + <warning>bar</warning>')
  }

  private def checkHighlighting(String text) {
    myFixture.configureByText('a.groovy', text)
    myFixture.enableInspections(new GroovyUnresolvedAccessInspection())
    myFixture.checkHighlighting(true, false, false)
  }
}
