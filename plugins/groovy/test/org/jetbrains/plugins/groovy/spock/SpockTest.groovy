package org.jetbrains.plugins.groovy.spock

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.util.containers.CollectionFactory
import com.intellij.psi.PsiFile

import com.intellij.psi.PsiVariable
import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessInspection

/**
 * @author Sergey Evdokimov
 */
class SpockTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected void setUp() {
    super.setUp()

    myFixture.addFileToProject("spock/lang/Specification.groovy", """
package spock.lang;

class Specification {

}
""")
  }



  public void testCompletion() {
    def file = myFixture.addFileToProject("FooSpec.groovy", """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    expect:
    <caret>

    where:
    varAssigment = "asdad"
    varShl << ['aaa', 'bbb']
    [varShl1, varShl2, varShl3] << [['aaa', 'bbb', 'ccc'], ['aaa', 'bbb', 'ccc'], ['aaa', 'bbb', 'ccc']]

    varTable1|varTable2|varTable3|varTable4
    ""|""|""|""
  }
}
""")

    checkCompletionStatic(file, "varAssigment", "varShl", "varShl1", "varShl2", "varShl3", "varTable1", "varTable2", "varTable3", "varTable4")
  }

  private void doTest(String expectedType, String text) {
    myFixture.configureByText("FooSpec.groovy", text)

    def var = myFixture.elementAtCaret
    assertInstanceOf(var, PsiVariable)
    assertEquals(expectedType, ((PsiVariable)var).type.canonicalText)
  }

  public void testEquals() {
    doTest(CommonClassNames.JAVA_LANG_STRING, """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    expect:
    name<caret>

    where:
    name = "xxx"
  }
}
""")
  }

  public void testTable() {
    doTest("java.util.List<? extends java.lang.Number>", """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    List<Byte> list = zzz()
    expect:
    name<caret>

    where:
    varTable1|varT.a.ble2|varTable3|name
    ""|""|""|list
    ""|""|""|[1,2L,3d]
    ""|""|""|null
  }
}
""")
  }

  public void testShlSimple() {
    doTest(CommonClassNames.JAVA_UTIL_MAP, """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    expect:
    name<caret>

    where:
    name << [new HashMap(), new TreeMap(), [aaa:1, bbb:2], null]
  }
}
""")
  }

  public void testShlMulti1() {
    doTest(CommonClassNames.JAVA_LANG_STRING, """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    def c = { return "1111" }

    expect:
    name<caret>

    where:
    [x1, _, name] << [['x', 'y', c()], ['x', 'y', null]]
  }
}
""")
  }

  public void testShlMulti2() {
    doTest(CommonClassNames.JAVA_LANG_STRING, """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    def list = ["a", "b", "c"]

    expect:
    name<caret>

    where:
    [x1, _, name] << [list, ['aaa', 'bbb', 'ccc']]
  }
}
""")
  }

  public void testRename() {
    myFixture.configureByText("FooSpec.groovy", """
class FooSpec extends spock.lang.Specification {
  @spock.lang.Unroll("xxx #name a #name #name    #name")
  def "foo test"() {
    expect:
    name == null || name.length() == 1

    where:
    [x1, _, name<caret>] << [['x', 'y', 'x'], ['x', 'y', null]]
  }
}
""")

    myFixture.renameElementAtCaret("n")

    myFixture.checkResult("""
class FooSpec extends spock.lang.Specification {
  @spock.lang.Unroll("xxx #n a #n #n    #n")
  def "foo test"() {
    expect:
    n == null || n.length() == 1

    where:
    [x1, _, n] << [['x', 'y', 'x'], ['x', 'y', null]]
  }
}
""")

    myFixture.renameElementAtCaret("z1234567890")

    myFixture.checkResult("""
class FooSpec extends spock.lang.Specification {
  @spock.lang.Unroll("xxx #z1234567890 a #z1234567890 #z1234567890    #z1234567890")
  def "foo test"() {
    expect:
    z1234567890 == null || z1234567890.length() == 1

    where:
    [x1, _, z1234567890] << [['x', 'y', 'x'], ['x', 'y', null]]
  }
}
""")

  }


  public void checkCompletionStatic(PsiFile file, String ... expectedVariants) {
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    LookupElement[] lookupElements = myFixture.completeBasic();

    assertNotNull(lookupElements);

    Set<String> missedVariants = CollectionFactory.newSet(expectedVariants);

    for (LookupElement lookupElement : lookupElements) {
      missedVariants.remove(lookupElement.getLookupString());
    }

    assertEmpty("Some completion variants are missed", missedVariants);
  }

  public void testVariable_resolved() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection, GroovyUnresolvedAccessInspection)

    myFixture.configureByText("FooSpec.groovy", """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    String subscriber = <warning>Mock</warning>()
    then: (0.._) * subscriber.concat(_)
      subscriber.concat(<warning>asdasdasd</warning>)
      subscriber.concat<warning>(23)</warning>
  }
}
""")

    myFixture.checkHighlighting(true, false, true)
  }

  public void testVariable_NotExistingInCompletion() {
    myFixture.configureByText("FooSpec.groovy", """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    String subscriber = Mock()
    then: (0.._) * subscriber.concat(<caret>)
  }
}
""")
    myFixture.completeBasic()
    def elements = myFixture.getLookupElementStrings()
    assert !elements.contains("_")
  }

}
