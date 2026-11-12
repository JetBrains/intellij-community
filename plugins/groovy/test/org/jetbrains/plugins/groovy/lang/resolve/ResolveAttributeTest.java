// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ExpressionTest;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveAttributeTest implements ExpressionTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @BeforeEach
  public void addClasses() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("A.groovy", """
        class A {
          static staticProp = 'static prop'
          public static staticField = 'static field'
      
          def instanceProp = 'instance prop'
          public instanceField = 'instance field'
      
          def getAccessorOnly() { 'accessor only' }
        }
        """);
    });
  }

  @Test
  public void instance_field_in_instance_context() {
    runInEdtAndWait(() -> resolveTest("new A().@<caret>instanceField", GrField.class));
  }

  @Test
  public void instance_property_in_instance_context() {
    runInEdtAndWait(() -> {
      GroovyResolveResult result = advancedResolveByText("new A().@<caret>instanceProp");
      assertTrue(result.getElement() instanceof GrField);
      assertFalse(result.isAccessible());
      assertFalse(result.isValidResult());
    });
  }

  @Test
  public void dont_resolve_accessor() {
    runInEdtAndWait(() -> resolveTest("new A().@<caret>accessorOnly", null));
  }

  @Test
  public void static_field_in_static_context() {
    runInEdtAndWait(() -> {
      resolveTest("A.@<caret>staticField", GrField.class);
      resolveTest("A.class.@<caret>staticField", GrField.class);
    });
  }

  @Test
  public void static_property_in_static_context() {
    runInEdtAndWait(() -> {
      resolveTest("A.@<caret>staticProp", GrField.class);
      resolveTest("A.class.@<caret>staticProp", GrField.class);
    });
  }

  @Test
  public void static_property_in_instance_context() {
    runInEdtAndWait(() -> resolveTest("new A().@<caret>staticProp", GrField.class));
  }

  @Test
  public void instance_property_in_static_context() {
    runInEdtAndWait(() -> {
      GroovyResolveResult result = advancedResolveByText("A.@<caret>instanceProp");
      assertTrue(result.getElement() instanceof GrField);
      assertFalse(result.isStaticsOK());
      assertFalse(result.isValidResult());
    });
  }

  @Test
  public void attribute_in_call() {
    runInEdtAndWait(() -> resolveTest("new A().@<caret>instanceField()", GrField.class));
  }

  @Test
  public void spread_attribute() {
    runInEdtAndWait(() -> referenceExpressionTest("[new A()]*.@instanceField", GrField.class, "java.util.ArrayList<java.lang.String>"));
  }

  @Test
  public void spread_attribute_deep() {
    runInEdtAndWait(() -> referenceExpressionTest("[[new A()]]*.@instanceField", null, "java.util.List"));
  }

  @Test
  public void spread_static_attribute() {
    runInEdtAndWait(() -> referenceExpressionTest("[A]*.@staticField", GrField.class, "java.util.ArrayList<java.lang.String>"));
  }
}
