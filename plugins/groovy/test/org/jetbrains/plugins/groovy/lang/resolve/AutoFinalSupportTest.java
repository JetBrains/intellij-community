// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.control.finalVar.GrFinalVariableAccessInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class AutoFinalSupportTest implements HighlightingTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  private void doTest(String text) {
    String newText = text;
    if (text.contains("AutoFinal")) {
      newText = "import groovy.transform.AutoFinal\n" + newText;
    }

    if (text.contains("CompileStatic")) {
      newText = "import groovy.transform.CompileStatic\n" + newText;
    }

    highlightingTest(newText, GrFinalVariableAccessInspection.class);
  }

  @Test
  public void autoFinalWorksOnFields() {
    runInEdtAndWait(() -> {
      doTest("""
               @AutoFinal
               @CompileStatic
               class Bar {
                   String s = ""
             
                   def foo() {
                       <error>s</error> = ""
                   }
               }
               """);
    });
  }

  @Test
  public void autoFinalWorksOnParameters() {
    runInEdtAndWait(() -> {
      doTest("""
               @AutoFinal
               @CompileStatic
               class Bar {
             
                   def foo(p) {
                       <error>p</error> = 1
                   }
               }
               """);
    });
  }

  @Test
  public void autoFinalWorksOnNestedClasses() {
    runInEdtAndWait(() -> {
      doTest("""
               @AutoFinal
               @CompileStatic
               class Foo {
             
                   static class Bar {
                       String s = ""
             
                       def foo() {
                           <error>s</error> = ""
                       }
                   }
             
               }
               """);
    });
  }

  @Test
  public void autoFinalWorksForClosureParameters() {
    runInEdtAndWait(() -> {
      doTest("""
               @AutoFinal
               @CompileStatic
               class Bar {
             
                   def foo() {
                       1.with {a -> <error descr="Cannot assign a value to final variable 'a'">a</error> = 2}
                   }
               }
               """);
    });
  }

  @Test
  public void disabledAutoFinal() {
    runInEdtAndWait(() -> {
      doTest("""
               @AutoFinal
               @CompileStatic
               class Bar {
             
                   @AutoFinal(enabled = false)
                   def foo(r) {
                       r = 2
                   }
               }""");
    });
  }
}
