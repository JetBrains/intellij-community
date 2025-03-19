// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

@Ignore
public class GroovyModificationTrackersTest extends GroovyLatestTest {
  @Test
  public void test_class_method() {
    doTest("""
             class A {
               def foo() { <caret> }
             }
             """, false, false);
  }

  @Test
  public void test_class_initializer() {
    doTest("""
             class A {
              {
                <caret>
              }
             }
             """, false, false);
  }

  @Test
  public void test_class_body() {
    doTest("""
             class A {
               <caret>
             }
             """, false, true);
  }

  @Test
  public void test_script_body() {
    doTest("<caret>", false, false);
  }

  @Test
  public void test_script_variable() {
    doTest("def a<caret>= 1", false, true);
  }

  public void doTest(String text, boolean structureShouldChange, boolean oocbShouldChange) {
    getFixture().configureByText("_.groovy", text);
    long beforeStructure = getJavaStructureCount();
    long beforeOutOfCodeBlock = getOutOfCodeBlockCount();

    List<Throwable> changeTraces = new ArrayList<>();
    if (!structureShouldChange && !oocbShouldChange) {
      PsiModificationTracker.Listener listener = () -> {
        String message = "java structure: " + getJavaStructureCount() + ", out of code block: " + getOutOfCodeBlockCount();
        changeTraces.add(new Throwable(message));
      };
      getFixture().getProject().getMessageBus().connect(getFixture().getTestRootDisposable())
        .subscribe(PsiModificationTracker.TOPIC, listener);
    }

    getFixture().type(" ");
    PsiDocumentManager.getInstance(getFixture().getProject()).commitDocument(getFixture().getEditor().getDocument());
    long afterStructure = getJavaStructureCount();
    long afterOutOfCodeBlock = getOutOfCodeBlockCount();

    try {
      if (structureShouldChange) {
        assert beforeStructure < afterStructure;
      }
      else {
        assert beforeStructure == afterStructure;
      }

      if (oocbShouldChange) {
        assert beforeOutOfCodeBlock < afterOutOfCodeBlock;
      }
      else {
        assert beforeOutOfCodeBlock == afterOutOfCodeBlock;
      }
    }
    catch (Throwable e) {
      for (Throwable trace : changeTraces) {
        trace.printStackTrace();
      }
      throw e;
    }
  }

  public void doTest(String text, boolean structureShouldChange) {
    doTest(text, structureShouldChange, structureShouldChange);
  }

  private long getJavaStructureCount() { return getFixture().getPsiManager().getModificationTracker().getModificationCount(); }

  private long getOutOfCodeBlockCount() { return getFixture().getPsiManager().getModificationTracker().getModificationCount(); }
}
