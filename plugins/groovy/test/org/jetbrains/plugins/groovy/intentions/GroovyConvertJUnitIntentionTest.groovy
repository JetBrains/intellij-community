package org.jetbrains.plugins.groovy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Sergey Evdokimov
 */
class GroovyConvertJUnitIntentionTest extends LightCodeInsightFixtureTestCase {

  public void testAssertFalse() {
    doTest("""
class A extends junit.framework.Assert {
  public void testXxxx() {
      assertFalse<caret>("!!!", 1 == 2)
  }
}
""", """
class A extends junit.framework.Assert {
  public void testXxxx() {
      assert !(1 == 2): "!!!"
  }
}
""")
  }

  public void testAssertEquals() {
    doTest("""
class A extends junit.framework.Assert {
  public void testXxxx() {
      assertEquals<caret>("!!!", 1, 2)
  }
}
""", """
class A extends junit.framework.Assert {
  public void testXxxx() {
      assert 1 == 2: "!!!"
  }
}
""")
  }

  public void testAssertNotSame() {
    doTest("""
class A extends junit.framework.Assert {
  public void testXxxx() {
      assertNotSame<caret>("1", "2")
  }
}
""", """
class A extends junit.framework.Assert {
  public void testXxxx() {
      assert !"1".is("2")
  }
}
""")
  }

  public void testFail() {
    doTest("""
class A extends junit.framework.Assert {
  public void testXxxx() {
      assertSame<caret>("1")
  }
}
""", null)
  }

  private void doTest(String before, String after) {
    myFixture.addFileToProject("junit/framework/Assert.groovy", """
package junit.framework;
public class Assert {
  public static void assertTrue(java.lang.String message, boolean condition) { }
  public static void assertTrue(boolean condition) { }
  public static void assertFalse(java.lang.String message, boolean condition) { }
  public static void assertEquals(java.lang.Object expected, java.lang.Object actual) { }
  public static void assertNotSame(java.lang.Object expected, java.lang.Object actual) { }
}
""")
    myFixture.configureByText("A.groovy", before);

    String hint = GroovyIntentionsBundle.message("convert.junit.assertion.to.assert.statement.intention.name");
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    if (after == null) {
      assertEmpty(list);
      return;
    }

    myFixture.launchAction(assertOneElement(list));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();

    myFixture.checkResult(after)
  }

}
