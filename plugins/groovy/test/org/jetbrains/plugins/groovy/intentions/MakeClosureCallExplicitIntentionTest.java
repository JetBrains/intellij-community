// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.plugins.groovy.util.BaseTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MakeClosureCallExplicitIntentionTest extends GroovyLatestTest implements BaseTest {
  private void doTest(String before, String after) {
    configureByText(before);
    List<IntentionAction> intentions =
      getFixture().filterAvailableIntentions(GroovyIntentionsBundle.message("make.closure.call.explicit.intention.name"));
    if (after == null) {
      assertTrue(intentions.isEmpty());
    }
    else {
      assertEquals(1, intentions.size());
      getFixture().launchAction(intentions.get(0));
      getFixture().checkResult(after);
    }
  }

  @Test
  public void closure_local_variable() {
    doTest("def local = {}; <caret>local()", "def local = {}; <caret>local.call()");
  }

  @Test
  public void non_closure_local_variable() {
    doTest("def local = \"\"; <caret>local()", null);
  }

  @Test
  public void callable_local_variable() {
    getFixture().addFileToProject("classes.groovy", "class Callable { def call() {} }");
    doTest("def local = new Callable(); <caret>local()", null);
  }

  @Test
  public void closure_property() {
    getFixture().addFileToProject("classes.groovy", "class A { def prop = {} }");
    doTest("new A().<caret>prop()", "new A().<caret>prop.call()");
  }

  @Test
  public void non_closure_property() {
    getFixture().addFileToProject("classes.groovy", "class A { def prop = \"\" }");
    doTest("new A().<caret>prop()", null);
  }

  @Test
  public void closure_method() {
    doTest("Closure foo() {}; <caret>foo()", null);
  }

  @Test
  public void closure_method_call() {
    doTest("Closure foo() {}; foo().<caret>call()", null);
  }
}
