// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class ResolvePropertyVersusTest implements ResolveTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void instanceGetterVsInstanceField() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", """
        class C {
          public foo = "field"
          def getFoo() { "getter" }
        }
        """);
      resolveTest("new C().<caret>foo", GrMethod.class);
      resolveTest("C.<caret>foo", GrField.class);
    });
  }

  @Test
  public void staticGetterVsStaticField() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", """
        class C {
          public static foo = "field"
          static def getFoo() { "getter" }
        }
        """);
      resolveTest("new C().<caret>foo", GrMethod.class);
      resolveTest("C.<caret>foo", GrMethod.class);
    });
  }

  @Test
  public void instanceGetterVsStaticField() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", """
        class C {
          public static foo = "field"
          def getFoo() { "getter" }
        }
        """);
      resolveTest("new C().<caret>foo", GrMethod.class);
      resolveTest("C.<caret>foo", GrField.class);
    });
  }

  @Test
  public void staticGetterVsInstanceField() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", """
        class C {
          public foo = "field"
          static def getFoo() { "getter" }
        }
        """);
      resolveTest("new C().<caret>foo", GrMethod.class);
      resolveTest("C.<caret>foo", GrMethod.class);
    });
  }
}
