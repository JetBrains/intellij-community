// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.swing;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public final class SwingBuilderTest implements ResolveTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_1);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void swingBuilderMethod() {
    runInEdtAndWait(() -> {
      PsiMethod method = resolveTest("new groovy.swing.SwingBuilder().<caret>frame()", PsiMethod.class);
      Assertions.assertFalse(method.isPhysical());
    });
  }

  @Test
  public void swingProperty() {
    runInEdtAndWait(() -> {
      PsiMethod method = resolveTest("new groovy.swing.SwingBuilder().table(<caret>autoscrolls: false)", PsiMethod.class);
      Assertions.assertTrue(PropertyUtil.isSimplePropertySetter(method));
      Assertions.assertEquals("javax.swing.JComponent", method.getContainingClass().getQualifiedName());
    });
  }
}
