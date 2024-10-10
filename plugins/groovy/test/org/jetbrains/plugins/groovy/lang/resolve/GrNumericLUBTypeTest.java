// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.LightProjectDescriptor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.LinkedHashMap;

public class GrNumericLUBTypeTest extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().addFileToProject("constants.groovy", """
      interface Constants {
        Byte nB = 0
        Short nS = 0
        Integer nI = 0
        Long nL = 0
        Float nF = 0
        Double nD = 0
      }
      """);
  }

  private void doTest(String expressionText, String expectedType) {
    GroovyFile file = DefaultGroovyMethods.asType(
      getFixture().configureByText("_.groovy", "import static Constants.*" + expressionText),
      GroovyFile.class
    );
    GrExpression expression = DefaultGroovyMethods.asType(DefaultGroovyMethods.last(file.getStatements()), GrExpression.class);
    assert expression.getType().equalsToText(expectedType) : "'" + expression.getText() + "' : " + expression.getType();
  }

  public void test_elvis_types() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(36);
    map.put("nB ?: nB", "java.lang.Byte");
    map.put("nB ?: nS", "java.lang.Short");
    map.put("nB ?: nI", "java.lang.Integer");
    map.put("nB ?: nL", "java.lang.Long");
    map.put("nB ?: nF", "java.lang.Float");
    map.put("nB ?: nD", "java.lang.Double");
    map.put("nS ?: nB", "java.lang.Short");
    map.put("nS ?: nS", "java.lang.Short");
    map.put("nS ?: nI", "java.lang.Integer");
    map.put("nS ?: nL", "java.lang.Long");
    map.put("nS ?: nF", "java.lang.Float");
    map.put("nS ?: nD", "java.lang.Double");
    map.put("nI ?: nB", "java.lang.Integer");
    map.put("nI ?: nS", "java.lang.Integer");
    map.put("nI ?: nI", "java.lang.Integer");
    map.put("nI ?: nL", "java.lang.Long");
    map.put("nI ?: nF", "java.lang.Float");
    map.put("nI ?: nD", "java.lang.Double");
    map.put("nL ?: nB", "java.lang.Long");
    map.put("nL ?: nS", "java.lang.Long");
    map.put("nL ?: nI", "java.lang.Long");
    map.put("nL ?: nL", "java.lang.Long");
    map.put("nL ?: nF", "java.lang.Float");
    map.put("nL ?: nD", "java.lang.Double");
    map.put("nF ?: nB", "java.lang.Float");
    map.put("nF ?: nS", "java.lang.Float");
    map.put("nF ?: nI", "java.lang.Float");
    map.put("nF ?: nL", "java.lang.Float");
    map.put("nF ?: nF", "java.lang.Float");
    map.put("nF ?: nD", "java.lang.Double");
    map.put("nD ?: nB", "java.lang.Double");
    map.put("nD ?: nS", "java.lang.Double");
    map.put("nD ?: nI", "java.lang.Double");
    map.put("nD ?: nL", "java.lang.Double");
    map.put("nD ?: nF", "java.lang.Double");
    map.put("nD ?: nD", "java.lang.Double");

    map.forEach((k, v) -> doTest(k, v));
  }

  public void test_ternary_types() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(6);
    map.put("42 ? nB : nB", "java.lang.Byte");
    map.put("42 ? nB : nS", "java.lang.Short");
    map.put("42 ? nB : nI", "java.lang.Integer");
    map.put("42 ? nB : nL", "java.lang.Long");
    map.put("42 ? nB : nF", "java.lang.Float");
    map.put("42 ? nB : nD", "java.lang.Double");

    map.forEach((k, v) -> doTest(k, v));
  }

  public void test_if_branches_assignment() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(6);
    map.put("def a; if (42) { a = nB } else { a = nB }; a", "java.lang.Byte");
    map.put("def a; if (42) { a = nB } else { a = nS }; a", "java.lang.Short");
    map.put("def a; if (42) { a = nB } else { a = nI }; a", "java.lang.Integer");
    map.put("def a; if (42) { a = nB } else { a = nL }; a", "java.lang.Long");
    map.put("def a; if (42) { a = nB } else { a = nF }; a", "java.lang.Float");
    map.put("def a; if (42) { a = nB } else { a = nD }; a", "java.lang.Double");

    map.forEach((k, v) -> doTest(k, v));
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;
}
