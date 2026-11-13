// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GroovyDynamicMembersTest implements ResolveTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @BeforeEach
  public void init_state() {
    DynamicElementSettings methodDescriptor = new DynamicElementSettings();
    runInEdtAndWait(() -> {
      methodDescriptor.setContainingClassName(JAVA_LANG_STRING);
      methodDescriptor.setName("foo");
      methodDescriptor.setType("void");
      methodDescriptor.setMethod(true);
      methodDescriptor.setParams(new ArrayList<>());
      // Collections.emptyList() // https://issues.apache.org/jira/browse/GROOVY-8961

      final DynamicElementSettings propertyDescriptor = new DynamicElementSettings();
      propertyDescriptor.setContainingClassName(JAVA_LANG_STRING);
      propertyDescriptor.setName("foo");

      DynamicManager manager = DynamicManager.getInstance(getProject());
      manager.addMethod(methodDescriptor);
      manager.addProperty(propertyDescriptor);
    });
  }

  @AfterEach
  public void clear_state() {
    runInEdtAndWait(() -> DynamicManager.getInstance(getProject()).loadState(new DRootElement()));
  }

  @Test
  public void resolve_to_dynamic_method() {
    resolveTest("\"hi\".<caret>foo()", PsiMethod.class);
  }

  @Test
  public void resolve_to_dynamic_property() {
    resolveTest("\"hi\".<caret>foo", PsiField.class);
  }
}
