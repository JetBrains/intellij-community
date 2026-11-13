// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.BaseTest;
import com.intellij.groovy.testFramework.GroovyAssertions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_ARRAY_LIST;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class SpreadOperatorTest implements BaseTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void spreadProperty() {
    GrReferenceExpression expression = lastExpression("[[['a']]]*.bytes", GrReferenceExpression.class);
    ReadAction.run(() -> {
      GroovyResolveResult[] results = expression.multiResolve(false);
      Assertions.assertEquals(1, results.length);
      Assertions.assertNotNull(results[0].getSpreadState());
      GroovyAssertions.assertType(JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<byte[]>>>",
                                  expression.getType());
    });
  }

  @Test
  public void implicitSpreadProperty() {
    GrReferenceExpression expression = lastExpression("[[['a']]].bytes", GrReferenceExpression.class);
    ReadAction.run(() -> {
      GroovyResolveResult[] results = expression.multiResolve(false);
      Assertions.assertEquals(1, results.length);
      Assertions.assertNotNull(results[0].getSpreadState());
      GroovyAssertions.assertType(JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<byte[]>>>",
                                  expression.getType());
    });
  }

  @Test
  public void spreadMethod() {
    GrMethodCall expression = lastExpression("[[['a']]]*.getBytes()", GrMethodCall.class);
    ReadAction.run(() -> {
      GroovyResolveResult[] results = expression.multiResolveGroovy(false);
      Assertions.assertEquals(0, results.length);
      GroovyAssertions.assertType(null, expression.getType());
    });
  }

  @Test
  public void implicitSpreadMethod() {
    GrMethodCall expression = lastExpression("[[['a']]].getBytes()", GrMethodCall.class);
    ReadAction.run(() -> {
      GroovyResolveResult[] results = expression.multiResolveGroovy(false);
      Assertions.assertEquals(0, results.length);
      GroovyAssertions.assertType(null, expression.getType());
    });
  }
}
