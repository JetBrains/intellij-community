// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Test;

import java.util.Collection;

public class ResolveAssertionsTest extends GroovyLatestTest implements ResolveTest {
  @Test
  public void test_substitutor_is_not_computed_within_resolve() {
    GroovyResolveResult result = advancedResolveByText("""
                                                       [1, 2, 3].with {
                                                         group<caret>By({2})
                                                       }
                                                       """);
    assert result instanceof MethodResolveResult;
    assert !((MethodResolveResult)result).getFullSubstitutorDelegate().isInitialized();
  }

  @Test
  public void no_recursion_when_resolving_l_value_of_operator_assignment() {
    RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable());
    Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                           class Plus {
                                                                             Plus plus(Plus p) {}
                                                                           }
                                                                           class Container {
                                                                             Plus getFoo() {}
                                                                             void setFoo(Plus l) {}
                                                                             void setFoo(String s) {}
                                                                           }
                                                                           def c = new Container()
                                                                           c.<caret>foo += new Plus()
                                                                           """);
    assert results.size() == 2;
  }

  @Test
  public void no_error_when_computing_applicability_of_a_method_with_ambiguous_generic_method_call_argument() {
    RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable());
    Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                           def foo(Integer i){}
                                                                           def foo(String s){}
                                                                           def <T> T bar(Integer i){}
                                                                           def <T> T bar(String s){}
                                                                           <caret>foo(bar())
                                                                           """);
    assert results.size() == 2;
  }
}
