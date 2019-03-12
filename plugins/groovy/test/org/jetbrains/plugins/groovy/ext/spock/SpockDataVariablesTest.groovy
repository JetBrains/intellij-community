// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.BaseTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import static com.intellij.testFramework.UsefulTestCase.assertContainsElements

@CompileStatic
class SpockDataVariablesTest extends SpockTestBase implements BaseTest {

  private Map<String, SpockVariableDescriptor> variableMap(String text) {
    def file = configureByText """\
class FooSpec extends spock.lang.Specification {
  def feature() {
    $text
  }
}
"""
    def spec = file.typeDefinitions.first()
    def feature = (GrMethod)spec.findMethodsByName("feature").first()
    return SpockUtils.getVariableMap(feature)
  }

  private void testVariableNames(String text, String... names) {
    def variables = variableMap(text)
    assert names.length == variables.size()
    assertContainsElements(variables.values()*.name, names)
  }

  @Test
  void 'where block with additional labels'() {
    testVariableNames 'foo: abc: where: bar = 1', 'bar'
  }

  @Test
  void 'derived parameterization'() {
    testVariableNames 'where: bar = 1', 'bar'
  }

  @Test
  void 'simple parameterization'() {
    testVariableNames 'where: bar << [1]', 'bar'
  }

  @Test
  void 'multi parameterization'() {
    testVariableNames 'where: [bar, baz] << [[1,2]]', 'bar', 'baz'
  }

  @Test
  void 'empty single column table'() {
    testVariableNames 'where: bar | _', 'bar'
  }

  @Test
  void 'empty multi column table'() {
    testVariableNames 'where: bar | baz | bad', 'bar', 'baz', 'bad'
  }

  @Test
  void 'derived parameterization after table'() {
    testVariableNames '''\
where: 
bar | baz | bad
1 | 2 | 3
foo = bar * baz
''', 'bar', 'baz', 'bad', 'foo'
  }

  @Test
  void 'derived parameterization with label after table'() {
    testVariableNames '''\
where: 
bar | baz | bad
1 | 2 | 3
and:
foo = bar * baz
''', 'bar', 'baz', 'bad', 'foo'
  }

  @Test
  void 'and label inside table'() {
    def variables = variableMap '''\
where:
bar | _
and:
1   | _
'''
    assert variables.size() == 1
    assert variables.values().first().type.equalsToText(JAVA_LANG_INTEGER)
  }
}
