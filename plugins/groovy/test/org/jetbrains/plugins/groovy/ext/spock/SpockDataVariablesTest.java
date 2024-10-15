// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.BaseTest;
import org.junit.Test;

import java.util.Map;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static junit.framework.TestCase.assertEquals;

public class SpockDataVariablesTest extends SpockTestBase implements BaseTest {

  private Map<String, SpockVariableDescriptor> variableMap(String text) {
    String fileText = """
                class FooSpec extends spock.lang.Specification {
                  def feature() {
                """ + text + """
                  }
                }
                """;
    var file = configureByText(fileText);
    var spec = file.getTypeDefinitions()[0];
    var feature = (GrMethod) spec.findMethodsByName("feature")[0];
    return SpockUtils.getVariableMap(feature);
  }

  private void testVariableNames(String text, String... names) {
    Map<String, SpockVariableDescriptor> variables = variableMap(text);
    assert names.length == variables.size();
    assertContainsElements(variables.values().stream().map(SpockVariableDescriptor::getName).toList(), names);
  }

  @Test
  public void whereBlockWithAdditionalLabels() {
    testVariableNames("foo: abc: where: bar = 1", "bar");
  }

  @Test
  public void derivedParameterization() {
    testVariableNames("where: bar = 1", "bar");
  }

  @Test
  public void simpleParameterization() {
    testVariableNames("where: bar << [1]", "bar");
  }

  @Test
  public void multiParameterization() {
    testVariableNames("where: [bar, baz] << [[1,2]]", "bar", "baz");
  }

  @Test
  public void emptySingleColumnTable() {
    testVariableNames("where: bar | _", "bar");
  }

  @Test
  public void emptyMultiColumnTable() {
    testVariableNames("where: bar | baz | bad", "bar", "baz", "bad");
  }

  @Test
  public void derivedParameterizationAfterTable() {
    testVariableNames("""
                where:
                bar | baz | bad
                1 | 2 | 3
                foo = bar * baz
                """, "bar", "baz", "bad", "foo");
  }

  @Test
  public void derivedParameterizationWithLabelAfterTable() {
    testVariableNames("""
                where:
                bar | baz | bad
                1 | 2 | 3
                and:
                foo = bar * baz
                """, "bar", "baz", "bad", "foo");
  }

  @Test
  public void andLabelInsideTable() {
    Map<String, SpockVariableDescriptor> variables = variableMap("""
                where:
                bar | _
                and:
                1   | _
                """);
    assertEquals(1, variables.size());
    assertEquals(JAVA_LANG_INTEGER, variables.values().iterator().next().getType().getCanonicalText());
  }
}