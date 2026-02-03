// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightProjectDescriptor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Iterator;
import java.util.List;

public class NumberMathTypingTest extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    addBigInteger();
    addBigDecimal();
  }

  public void test__() {
    List<List<String>> data = List.<List<String>>of(List.of("Byte", "Byte", "Integer"),
                                                    List.of("Byte", "Character", "Integer"),
                                                    List.of("Byte", "Short", "Integer"),
                                                    List.of("Byte", "Integer", "Integer"),
                                                    List.of("Byte", "Long", "Long"),
                                                    List.of("Byte", "BigInteger", "BigInteger"),
                                                    List.of("Byte", "BigDecimal", "BigDecimal"),
                                                    List.of("Byte", "Float", "Double"),
                                                    List.of("Byte", "Double", "Double"),
                                                    List.of("Character", "Byte", "Integer"),
                                                    List.of("Character", "Character", "Integer"),
                                                    List.of("Character", "Short", "Integer"),
                                                    List.of("Character", "Integer", "Integer"),
                                                    List.of("Character", "Long", "Long"),
                                                    List.of("Character", "BigInteger", "BigInteger"),
                                                    List.of("Character", "BigDecimal", "BigDecimal"),
                                                    List.of("Character", "Float", "Double"),
                                                    List.of("Character", "Double", "Double"),
                                                    List.of("Short", "Byte", "Integer"),
                                                    List.of("Short", "Character", "Integer"),
                                                    List.of("Short", "Short", "Integer"),
                                                    List.of("Short", "Integer", "Integer"),
                                                    List.of("Short", "Long", "Long"),
                                                    List.of("Short", "BigInteger", "BigInteger"),
                                                    List.of("Short", "BigDecimal", "BigDecimal"),
                                                    List.of("Short", "Float", "Double"),
                                                    List.of("Short", "Double", "Double"),
                                                    List.of("Integer", "Byte", "Integer"),
                                                    List.of("Integer", "Character", "Integer"),
                                                    List.of("Integer", "Short", "Integer"),
                                                    List.of("Integer", "Integer", "Integer"),
                                                    List.of("Integer", "Long", "Long"),
                                                    List.of("Integer", "BigInteger", "BigInteger"),
                                                    List.of("Integer", "BigDecimal", "BigDecimal"),
                                                    List.of("Integer", "Float", "Double"),
                                                    List.of("Integer", "Double", "Double"),
                                                    List.of("Long", "Byte", "Long"),
                                                    List.of("Long", "Character", "Long"),
                                                    List.of("Long", "Short", "Long"),
                                                    List.of("Long", "Integer", "Long"),
                                                    List.of("Long", "Long", "Long"),
                                                    List.of("Long", "BigInteger", "BigInteger"),
                                                    List.of("Long", "BigDecimal", "BigDecimal"),
                                                    List.of("Long", "Float", "Double"),
                                                    List.of("Long", "Double", "Double"),
                                                    List.of("BigInteger", "Byte", "BigInteger"),
                                                    List.of("BigInteger", "Character", "BigInteger"),
                                                    List.of("BigInteger", "Short", "BigInteger"),
                                                    List.of("BigInteger", "Integer", "BigInteger"),
                                                    List.of("BigInteger", "Long", "BigInteger"),
                                                    List.of("BigInteger", "BigInteger", "BigInteger"),
                                                    List.of("BigInteger", "BigDecimal", "BigDecimal"),
                                                    List.of("BigInteger", "Float", "Double"),
                                                    List.of("BigInteger", "Double", "Double"),
                                                    List.of("BigDecimal", "Byte", "BigDecimal"),
                                                    List.of("BigDecimal", "Character", "BigDecimal"),
                                                    List.of("BigDecimal", "Short", "BigDecimal"),
                                                    List.of("BigDecimal", "Integer", "BigDecimal"),
                                                    List.of("BigDecimal", "Long", "BigDecimal"),
                                                    List.of("BigDecimal", "BigInteger", "BigDecimal"),
                                                    List.of("BigDecimal", "BigDecimal", "BigDecimal"),
                                                    List.of("BigDecimal", "Float", "Double"),
                                                    List.of("BigDecimal", "Double", "Double"),
                                                    List.of("Float", "Byte", "Double"),
                                                    List.of("Float", "Character", "Double"),
                                                    List.of("Float", "Short", "Double"),
                                                    List.of("Float", "Integer", "Double"),
                                                    List.of("Float", "Long", "Double"),
                                                    List.of("Float", "BigInteger", "Double"),
                                                    List.of("Float", "BigDecimal", "Double"),
                                                    List.of("Float", "Float", "Double"),
                                                    List.of("Float", "Double", "Double"),
                                                    List.of("Double", "Byte", "Double"),
                                                    List.of("Double", "Character", "Double"),
                                                    List.of("Double", "Short", "Double"),
                                                    List.of("Double", "Integer", "Double"),
                                                    List.of("Double", "Long", "Double"),
                                                    List.of("Double", "BigInteger", "Double"),
                                                    List.of("Double", "BigDecimal", "Double"),
                                                    List.of("Double", "Float", "Double"),
                                                    List.of("Double", "Double", "Double"));
    for (List<String> row : data) {
      final Iterator<String> iterator = row.iterator();
      String left = iterator.hasNext() ? iterator.next() : null;
      String right = iterator.hasNext() ? iterator.next() : null;
      String type = iterator.hasNext() ? iterator.next() : null;

      doTest(left, "+", right, type);
    }
  }

  private void doTest(final String left, final String operator, final String right, final String expected) {
    GroovyFile file = (GroovyFile)myFixture.configureByText("_.groovy", "def foo(" + left + " a, " + right + " b) {\n" +
                                                                        "  a " + operator + " b\n" +
                                                                        "}\n");
    try {
      GrStatement expr = DefaultGroovyMethods.first(DefaultGroovyMethods.first(file.getMethods()).getBlock().getStatements());
      assertTrue(expr instanceof GrExpression);
      PsiType type = ((GrExpression)expr).getType();
      assertTrue(type instanceof PsiClassType);
      String name = ((PsiClassType)type).getClassName();
      assertEquals(expected, name);
    }
    catch (Throwable e) {
      throw new RuntimeException(left + " " + operator + " " + right + " (expected: " + expected + ")", e);
    }
  }

  public void test_resolve_Number_Number_math_methods() {
    getFixture().configureByText("_.groovy", """
        def foo(Number m, Number n) {
          m.plus n
          m.minus n
          m.div n
          m.multiply n
        }
        """);
    getFixture().enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
    getFixture().checkHighlighting();
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
