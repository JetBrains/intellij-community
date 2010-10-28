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

  public void testGenerallyFocusLookup() {
    myFixture.configureByText("a.groovy", """
        String foo(String xxxxxx) {
          return xx<caret>
        }
    """)
    type 'x'
    assert lookup.focused
  }

  public void testNoLookupFocusInVariable() {
    myFixture.configureByText("a.groovy", """StringBuffer st<caret>""")
    type 'r'
    assert !lookup.focused
  }

  public void testNoLookupFocusOnUnresolvedQualifier() {
    myFixture.configureByText("a.groovy", """xxx.<caret>""")
    type 'h' //hashCode
    assert !lookup.focused
  }

  public void testNoLookupFocusOnUntypedQualifier() {
    myFixture.configureByText("a.groovy", """
      def foo(xxx) {
        xxx.<caret>
      }""")
    type 'h'
    assert !lookup.focused
  }

}
