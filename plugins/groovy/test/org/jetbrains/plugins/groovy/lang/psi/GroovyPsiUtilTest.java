// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Test;

import java.util.LinkedHashMap;

import static junit.framework.TestCase.assertEquals;

public class GroovyPsiUtilTest extends GroovyLatestTest {
  @Test
  public void application_expression() {
    LinkedHashMap<String, Boolean> map = new LinkedHashMap<>(20);
    map.put("foo", false);
    map.put("foo.ref", false);
    map.put("foo(a)", false);
    map.put("foo[a]", false);
    map.put("foo a", true);
    map.put("foo(a) ref", true);
    map.put("foo(a).ref", false);
    map.put("foo a ref c", true);
    map.put("foo a ref(c)", true);
    map.put("foo(a) ref(c)", true);
    map.put("foo(a).ref(c)", false);
    map.put("foo a ref[c]", true);
    map.put("foo(a) ref[c]", true);
    map.put("foo(a).ref[c]", false);
    map.put("foo a ref[c] ref", true);
    map.put("foo a ref[c] (a)", true);
    map.put("foo a ref[c] {}", true);
    map.put("foo a ref(c) ref", true);
    map.put("foo a ref(c)(c)", true);
    map.put("foo a ref(c)[c]", true);
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getFixture().getProject());
    TestUtils.runAll(map, (expressionText, isApplication) -> {
      GrExpression expression = factory.createExpressionFromText(expressionText);
      assertEquals(PsiUtilKt.isApplicationExpression(expression), (boolean)isApplication);
    });
  }
}
