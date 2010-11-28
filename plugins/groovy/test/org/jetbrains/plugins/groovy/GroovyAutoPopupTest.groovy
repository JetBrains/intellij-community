package org.jetbrains.plugins.groovy

import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase
import com.intellij.testFramework.LightProjectDescriptor

/**
 * @author peter
 */
class GroovyAutoPopupTest extends CompletionAutoPopupTestCase {
  @Override protected LightProjectDescriptor getProjectDescriptor() {
    return LightGroovyTestCase.GROOVY_DESCRIPTOR
  }

  public void testGenerallyNoFocusLookup() {
    myFixture.configureByText("a.groovy", """
        String foo(String xxxxxx) {
          return xx<caret>
        }
    """)
    type 'x'
    assert !lookup.focused
  }

  public void testGenerallyFocusLookupInGpp() {
    myFixture.configureByText("a.gpp", """
        String foo(String xxxxxx) {
          return xx<caret>
        }
    """)
    type 'x'
    assert lookup.focused
  }

  public void testNoLookupFocusInVariable() {
    myFixture.configureByText("a.gpp", """StringBuffer st<caret>""")
    type 'r'
    assert !lookup.focused
  }

  public void testNoLookupFocusOnUnresolvedQualifier() {
    myFixture.configureByText("a.gpp", """xxx.<caret>""")
    type 'h' //hashCode
    assert !lookup.focused
  }

  public void testNoLookupFocusOnUntypedQualifier() {
    myFixture.configureByText("a.gpp", """
      def foo(xxx) {
        xxx.<caret>
      }""")
    type 'h'
    assert !lookup.focused
  }

  public void testPossibleClosureParameter() {
    myFixture.configureByText("a.gpp", "{ <caret> }")
    type 'h'
    assert !lookup.focused
  }

  public void testPossibleClosureParameter2() {
    myFixture.configureByText("a.gpp", "{ a, <caret> }")
    type 'h'
    assert !lookup
  }

  public void testImpossibleClosureParameter() {
    myFixture.configureByText("a.gpp", "String a; { a.<caret> }")
    type 'h'
    assert lookup.focused
  }

}
