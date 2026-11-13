// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.groovy.testFramework.BaseTest;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.intellij.groovy.testFramework.GroovyAssertions.assertContainsElements;
import static com.intellij.groovy.testFramework.GroovyAssertions.assertSize;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class SpockDataVariablesTest implements BaseTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(SpockTestUtil.SPOCK_PROJECT);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

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
    assertSize(names.length, variables.entrySet());
    assertContainsElements(ContainerUtil.map(variables.values(), SpockVariableDescriptor::getName), names);
  }

  @Test
  public void whereBlockWithAdditionalLabels() {
    runInEdtAndWait(() -> testVariableNames("foo: abc: where: bar = 1", "bar"));
  }

  @Test
  public void derivedParameterization() {
    runInEdtAndWait(() -> testVariableNames("where: bar = 1", "bar"));
  }

  @Test
  public void simpleParameterization() {
    runInEdtAndWait(() -> testVariableNames("where: bar << [1]", "bar"));
  }

  @Test
  public void multiParameterization() {
    runInEdtAndWait(() -> testVariableNames("where: [bar, baz] << [[1,2]]", "bar", "baz"));
  }

  @Test
  public void emptySingleColumnTable() {
    runInEdtAndWait(() -> testVariableNames("where: bar | _", "bar"));
  }

  @Test
  public void emptyMultiColumnTable() {
    runInEdtAndWait(() -> testVariableNames("where: bar | baz | bad", "bar", "baz", "bad"));
  }

  @Test
  public void derivedParameterizationAfterTable() {
    runInEdtAndWait(() -> {
      testVariableNames("""
                  where:
                  bar | baz | bad
                  1 | 2 | 3
                  foo = bar * baz
                  """, "bar", "baz", "bad", "foo");
    });
  }

  @Test
  public void derivedParameterizationWithLabelAfterTable() {
    runInEdtAndWait(() -> {
      testVariableNames("""
                  where:
                  bar | baz | bad
                  1 | 2 | 3
                  and:
                  foo = bar * baz
                  """, "bar", "baz", "bad", "foo");
    });
  }

  @Test
  public void andLabelInsideTable() {
    runInEdtAndWait(() -> {
      Map<String, SpockVariableDescriptor> variables = variableMap("""
                  where:
                  bar | _
                  and:
                  1   | _
                  """);
      assertEquals(1, variables.size());
      assertEquals(JAVA_LANG_INTEGER, variables.values().iterator().next().getType().getCanonicalText());
    });
  }
}