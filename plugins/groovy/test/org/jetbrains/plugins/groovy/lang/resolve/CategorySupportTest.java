// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class CategorySupportTest implements ResolveTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void staticCategoryMethodOutsideOfCategory() {
    runInEdtAndWait(() -> {
      GrReflectedMethod resolved = resolveTest(
        """
          @Category(String) class C { def foo() {} }
          C.<caret>foo("")
          """, GrReflectedMethod.class);
      Assertions.assertTrue(resolved.getModifierList().hasModifierProperty(PsiModifier.STATIC));
    });
  }

  @Test
  public void categoryMethodWithinCategory() {
    runInEdtAndWait(() -> {
      resolveTest(
        """
          @Category(String)
          class C {
            def foo() {}
            def usage() { <caret>foo() }
          }
          """, GrGdkMethod.class);
    });
  }

  @Test
  public void explicitCategoryMethodWithinCategory() {
    runInEdtAndWait(() -> {
      resolveTest(
        """
          @Category(String)
          class C {
            static def foo(String s) {}
            def usage() { <caret>foo() }
          }
          """, GrGdkMethod.class);
    });
  }

  @Test
  public void propertyWithinCategoryWithExplicitMethod() {
    runInEdtAndWait(() -> {
      resolveTest(
        """
          @Category(String)
          class C {
            static def foo(String s) {}
            def usage() { <caret>foo }
          }
          """, null);
    });
  }
}
