// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.EdtRule
import org.jetbrains.plugins.groovy.util.FixtureRule
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

@CompileStatic
class ResolveAssertionsTest implements ResolveTest {

  public final FixtureRule myFixtureRule = new FixtureRule(GroovyProjectDescriptors.GROOVY_2_3, '')
  public final @Rule TestRule myRules = RuleChain.outerRule(myFixtureRule).around(new EdtRule())

  @NotNull
  @Override
  CodeInsightTestFixture getFixture() { myFixtureRule.fixture }

  @Test
  void 'applicability not computed for single result'() {
    def result = advancedResolveByText '''\
static void foo(String s, Closure c) {}
<caret>foo {
  bar 
}
'''
    assert result instanceof MethodResolveResult
    assert !result.getApplicabilityDelegate().initialized
  }

  @Test
  void 'applicability is computed when two results'() {
    def result = advancedResolveByText '''\
static void foo(String s, Closure c) {}
static void foo(Closure c) {} 
<caret>foo {
  bar 
}
'''
    assert result instanceof MethodResolveResult
    assert result.getApplicabilityDelegate().initialized
  }

  @Test
  void 'test substitutor is not computed within resolve'() {
    def result = advancedResolveByText '''\
[1, 2, 3].with {
  group<caret>By({2})
}
'''
    assert result instanceof MethodResolveResult
    assert !result.getFullSubstitutorDelegate().initialized
  }
}
