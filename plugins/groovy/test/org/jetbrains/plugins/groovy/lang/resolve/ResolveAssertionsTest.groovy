// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.RecursionManager
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
  void 'test substitutor is not computed within resolve'() {
    def result = advancedResolveByText '''\
[1, 2, 3].with {
  group<caret>By({2})
}
'''
    assert result instanceof MethodResolveResult
    assert !result.getFullSubstitutorDelegate().initialized
  }

  @Test
  void 'no recursion when resolving l-value of operator assignment'() {
    RecursionManager.assertOnRecursionPrevention(fixture.testRootDisposable)
    def results = multiResolveByText '''\
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
'''
    assert results.size() == 2
  }

  @Test
  void 'no error when computing applicability of a method with ambiguous generic method call argument'() {
    RecursionManager.assertOnRecursionPrevention(fixture.testRootDisposable)
    def results = multiResolveByText '''\
def foo(Integer i){}
def foo(String s){}
def <T> T bar(Integer i){}
def <T> T bar(String s){}
<caret>foo(bar())
'''
    assert results.size() == 2
  }
}
