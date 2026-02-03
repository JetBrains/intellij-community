// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Assert;
import org.junit.Test;

public class ResolveMethod2Test extends GroovyLatestTest implements ResolveTest {
  @Test
  public void voidArgument() {
    GroovyResolveResult result = advancedResolveByText("def foo(p); void bar(); <caret>foo(bar())");
    Assert.assertTrue(result instanceof GroovyMethodResult);
    Assert.assertEquals(Applicability.applicable, ((GroovyMethodResult)result).getApplicability());
  }
}
