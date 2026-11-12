// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static com.intellij.groovy.testFramework.GroovyAssertions.assertSize;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class ResolveAssertionsTest implements ResolveTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void test_substitutor_is_not_computed_within_resolve() {
    runInEdtAndWait(() -> {
      GroovyResolveResult result = advancedResolveByText("""
                                                         [1, 2, 3].with {
                                                           group<caret>By({2})
                                                         }
                                                         """);
      assertInstanceOf(MethodResolveResult.class, result);
      Assertions.assertFalse(((MethodResolveResult)result).getFullSubstitutorDelegate().isInitialized());
    });
  }

  @Test
  public void no_recursion_when_resolving_l_value_of_operator_assignment() {
    runInEdtAndWait(() -> {
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
      assertSize(2, results);
    });
  }

  @Test
  public void no_error_when_computing_applicability_of_a_method_with_ambiguous_generic_method_call_argument() {
    runInEdtAndWait(() -> {
      RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable());
      Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                             def foo(Integer i){}
                                                                             def foo(String s){}
                                                                             def <T> T bar(Integer i){}
                                                                             def <T> T bar(String s){}
                                                                             <caret>foo(bar())
                                                                             """);
      assertSize(2, results);
    });
  }
}
