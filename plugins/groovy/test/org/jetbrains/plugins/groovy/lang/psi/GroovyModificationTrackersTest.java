// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@Disabled
@TestApplication
public class GroovyModificationTrackersTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void test_class_method() {
    runInEdtAndWait(() -> {
      doTest("""
               class A {
                 def foo() { <caret> }
               }
               """, false, false);
    });
  }

  @Test
  public void test_class_initializer() {
    runInEdtAndWait(() -> {
      doTest("""
               class A {
                {
                  <caret>
                }
               }
               """, false, false);
    });
  }

  @Test
  public void test_class_body() {
    runInEdtAndWait(() -> {
      doTest("""
               class A {
                 <caret>
               }
               """, false, true);
    });
  }

  @Test
  public void test_script_body() {
    runInEdtAndWait(() -> {
      doTest("<caret>", false, false);
    });
  }

  @Test
  public void test_script_variable() {
    runInEdtAndWait(() -> {
      doTest("def a<caret>= 1", false, true);
    });
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
