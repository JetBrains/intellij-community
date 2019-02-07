// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiVariable
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

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
    myFixture.addClass '''\
package groovy.lang;
public class Closure<T> {
  T call(Object ... args) { return null; }
}
'''
  }


  void testCompletion() {
    def file = myFixture.addFileToProject("FooSpec.groovy", """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    expect:
    <caret>

    where:
    varAssigment = "asdad"
    varShl << ['aaa', 'bbb']
    [varShl1, varShl2, varShl3] << [['aaa', 'bbb', 'ccc'], ['aaa', 'bbb', 'ccc'], ['aaa', 'bbb', 'ccc']]

    varTable1|varTable2|varTable3||varTable4
    ""|""|""||""
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

  void testEquals() {
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

  void testTable() {
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

  void testShlSimple() {
    doTest(AbstractMap.name, """
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

  void testShlMulti1() {
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

  void testShlMulti2() {
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

  void testRename() {
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


  void checkCompletionStatic(PsiFile file, String... expectedVariants) {
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())

    LookupElement[] lookupElements = myFixture.completeBasic()

    assertNotNull(lookupElements)

    Set<String> missedVariants = ContainerUtil.newHashSet(expectedVariants)

    for (LookupElement lookupElement : lookupElements) {
      missedVariants.remove(lookupElement.getLookupString())
    }

    assertEmpty("Some completion variants are missed", missedVariants)
  }

  void testVariable_resolved() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection, GrUnresolvedAccessInspection)

    myFixture.configureByText("FooSpec.groovy", """\
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    String subscriber = <warning descr="Cannot resolve symbol 'Mock'">Mock</warning>()
    then: (0.._) * subscriber.concat<weak_warning descr="Cannot infer argument types">(_)</weak_warning>
      subscriber.concat<weak_warning descr="Cannot infer argument types">(<warning descr="Cannot resolve symbol 'asdasdasd'">asdasdasd</warning>)</weak_warning>
      subscriber.concat<warning descr="'concat' in 'java.lang.String' cannot be applied to '(java.lang.Integer)'">(23)</warning>
  }
}
""")

    myFixture.checkHighlighting(true, false, true)
  }

  void testVariable_NotExistingInCompletion() {
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

  void 'test method may be static'() {
    myFixture.configureByText 'specs.groovy', '''\
class SomeSpec extends spock.lang.Specification {
  def cleanup() {}
  def setupSpec() {}
  def <warning descr="Method may be static">regularMethod</warning>() {}
  def featureMethod() {
    expect: 1 == 1
  }
}
'''
    def inspection = new GrMethodMayBeStaticInspection()
    inspection.myIgnoreEmptyMethods = false
    myFixture.enableInspections inspection
    myFixture.checkHighlighting()
  }
}
