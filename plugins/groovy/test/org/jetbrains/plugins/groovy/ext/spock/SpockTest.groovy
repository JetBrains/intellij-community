// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Ignore
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

@CompileStatic
class SpockTest extends SpockTestBase implements TypingTest, HighlightingTest {

  @Test
  void testCompletion() {
    configureByText '''\
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
'''
    fixture.completeBasic()
    fixture.assertPreferredCompletionItems(
      0,
      "varAssigment", "varShl", "varShl1", "varShl2", "varShl3", "varTable1", "varTable2", "varTable3", "varTable4"
    )
  }

  @Test
  void testEquals() {
    typingTest '''\
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    expect:
    <caret>name

    where:
    name = "xxx"
  }
}
''', JAVA_LANG_STRING
  }

  @Test
  void testTable() {
    typingTest '''\
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    List<Byte> list = zzz()
    expect:
    <caret>name

    where:
    varTable1|varT.a.ble2|varTable3|name
    ""|""|""|list
    ""|""|""|[1,2L,3d]
    ""|""|""|null
  }
}
''', 'java.util.List<? extends java.lang.Number>'
  }

  @Test
  void testShlSimple() {
    typingTest '''\
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    expect:
    <caret>name

    where:
    name << [new HashMap(), new TreeMap(), [aaa:1, bbb:2], null]
  }
}
''', '[java.util.AbstractMap,java.lang.Cloneable,java.io.Serializable]'
  }

  @Test
  void testShlMulti1() {
    typingTest '''\
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    def c = { return "1111" }

    expect:
    <caret>name

    where:
    [x1, _, name] << [['x', 'y', c()], ['x', 'y', null]]
  }
}
''', JAVA_LANG_STRING
  }

  @Test
  void testShlMulti2() {
    typingTest '''\
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    def list = ["a", "b", "c"]

    expect:
    <caret>name

    where:
    [x1, _, name] << [list, ['aaa', 'bbb', 'ccc']]
  }
}
''', JAVA_LANG_STRING
  }

  @Test
  void testRename() {
    fixture.configureByText("FooSpec.groovy", """
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

    fixture.renameElementAtCaret("n")

    fixture.checkResult("""
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

    fixture.renameElementAtCaret("z1234567890")

    fixture.checkResult("""
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

  @Test
  void testVariable_NotExistingInCompletion() {
    fixture.configureByText("FooSpec.groovy", """
class FooSpec extends spock.lang.Specification {
  def "foo test"() {
    String subscriber = Mock()
    then: (0.._) * subscriber.concat(<caret>)
  }
}
""")
    fixture.completeBasic()
    def elements = fixture.getLookupElementStrings()
    assert elements.contains("_")
  }

  @Ignore("see com.intellij.execution.junit2.inspection.JUnitCantBeStaticExtension")
  @Test
  void 'test method may be static'() {
    def inspection = new GrMethodMayBeStaticInspection()
    inspection.myIgnoreEmptyMethods = false
    highlightingTest '''\
class SomeSpec extends spock.lang.Specification {
  def cleanup() {}
  def setupSpec() {}
  def <warning descr="Method may be static">regularMethod</warning>() {}
  def featureMethod() {
    expect: 1 == 1
  }
}
''', inspection
  }
}
