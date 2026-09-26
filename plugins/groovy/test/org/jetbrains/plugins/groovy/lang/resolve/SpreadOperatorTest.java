// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.BaseTest;
import com.intellij.groovy.testFramework.GroovyAssertions;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Assert;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_ARRAY_LIST;

public class SpreadOperatorTest extends GroovyLatestTest implements BaseTest {

  @Test
  public void spreadProperty() {
    GrReferenceExpression expression = lastExpression("[[['a']]]*.bytes", GrReferenceExpression.class);
    GroovyResolveResult[] results = expression.multiResolve(false);
    Assert.assertEquals(1, results.length);
    Assert.assertNotNull(results[0].getSpreadState());
    GroovyAssertions.assertType(JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<byte[]>>>",
                                expression.getType());
  }

  @Test
  public void implicitSpreadProperty() {
    GrReferenceExpression expression = lastExpression("[[['a']]].bytes", GrReferenceExpression.class);
    GroovyResolveResult[] results = expression.multiResolve(false);
    Assert.assertEquals(1, results.length);
    Assert.assertNotNull(results[0].getSpreadState());
    GroovyAssertions.assertType(JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<byte[]>>>",
                                expression.getType());
  }

  @Test
  public void spreadMethod() {
    GrMethodCall expression = lastExpression("[[['a']]]*.getBytes()", GrMethodCall.class);
    GroovyResolveResult[] results = expression.multiResolveGroovy(false);
    Assert.assertEquals(0, results.length);
    GroovyAssertions.assertType(null, expression.getType());
  }

  @Test
  public void implicitSpreadMethod() {
    GrMethodCall expression = lastExpression("[[['a']]].getBytes()", GrMethodCall.class);
    GroovyResolveResult[] results = expression.multiResolveGroovy(false);
    Assert.assertEquals(0, results.length);
    GroovyAssertions.assertType(null, expression.getType());
  }
}
