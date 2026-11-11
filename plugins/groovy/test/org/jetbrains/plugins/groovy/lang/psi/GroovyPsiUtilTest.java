// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GroovyPsiUtilTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5);

  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void application_expression() {
    runInEdtAndWait(() -> {
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
        assertEquals(PsiUtilKt.isApplicationExpression(expression), isApplication);
      });
    });
  }
}
