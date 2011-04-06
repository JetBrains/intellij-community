/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang;


import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyConstantIfStatementInspection
import org.jetbrains.plugins.groovy.codeInspection.gpath.GroovySetterCallCanBePropertyAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessInspection

/**
 * @author peter
 */
public class GroovyFixesTest extends LightCodeInsightFixtureTestCase {

  public void testSuppressForIfStatement() throws Throwable {
    myFixture.enableInspections new GroovyConstantIfStatementInspection()
    myFixture.configureByText("a.groovy", """
<caret>if (true) {
  aaa
}""")
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for statement"))
    myFixture.checkResult """//noinspection GroovyConstantIfStatement
if (true) {
  aaa
}"""
  }

  public void testShallowChangeToGroovyStylePropertyAccess() throws Throwable {
    myFixture.enableInspections new GroovySetterCallCanBePropertyAccessInspection()
    myFixture.configureByText "a.groovy", """class GroovyClasss {
  def initializer
  def foo() {
    setInitializer({
      <caret>println "hello"
    })
  }
}

"""
    assertEmpty myFixture.filterAvailableIntentions("Change to Groovy-style property reference")
  }

  public void testSecondAnnotationSuppression() {
    myFixture.enableInspections new GroovyUnresolvedAccessInspection()
    myFixture.configureByText "a.groovy", """class FooBarGoo {
  @SuppressWarnings(["GroovyParameterNamingConvention"])
  def test(def abc) {
    abc.d<caret>ef()
  }
}
"""
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for method"))
    myFixture.checkResult """class FooBarGoo {
  @SuppressWarnings(["GroovyParameterNamingConvention", "GroovyUnresolvedAccess"])
  def test(def abc) {
    abc.def()
  }
}
"""
  }

  public void testSecondAnnotationSuppression2() {
    myFixture.enableInspections new GroovyUnresolvedAccessInspection()
    myFixture.configureByText "a.groovy", """class FooBarGoo {
  @SuppressWarnings("GroovyParameterNamingConvention")
  def test(def abc) {
    abc.d<caret>ef()
  }
}
"""
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for method"))
    myFixture.checkResult """class FooBarGoo {
  @SuppressWarnings(["GroovyParameterNamingConvention", "GroovyUnresolvedAccess"])
  def test(def abc) {
    abc.def()
  }
}
"""
  }

}
