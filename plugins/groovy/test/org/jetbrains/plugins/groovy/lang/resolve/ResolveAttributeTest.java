// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.util.ExpressionTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class ResolveAttributeTest extends GroovyLatestTest implements ExpressionTest {
  @Before
  public void addClasses() {
    getFixture().addFileToProject("A.groovy", """
      class A {
        static staticProp = 'static prop'
        public static staticField = 'static field'
      
        def instanceProp = 'instance prop'
        public instanceField = 'instance field'
      
        def getAccessorOnly() { 'accessor only' }
      }
      """);
  }

  @Test
  public void instance_field_in_instance_context() {
    resolveTest("new A().@<caret>instanceField", GrField.class);
  }

  @Test
  public void instance_property_in_instance_context() {
    GroovyResolveResult result = advancedResolveByText("new A().@<caret>instanceProp");
    assertTrue(result.getElement() instanceof GrField);
    assertFalse(result.isAccessible());
    assertFalse(result.isValidResult());
  }

  @Test
  public void dont_resolve_accessor() {
    resolveTest("new A().@<caret>accessorOnly", null);
  }

  @Test
  public void static_field_in_static_context() {
    resolveTest("A.@<caret>staticField", GrField.class);
    resolveTest("A.class.@<caret>staticField", GrField.class);
  }

  @Test
  public void static_property_in_static_context() {
    resolveTest("A.@<caret>staticProp", GrField.class);
    resolveTest("A.class.@<caret>staticProp", GrField.class);
  }

  @Test
  public void static_property_in_instance_context() {
    resolveTest("new A().@<caret>staticProp", GrField.class);
  }

  @Test
  public void instance_property_in_static_context() {
    GroovyResolveResult result = advancedResolveByText("A.@<caret>instanceProp");
    assertTrue(result.getElement() instanceof GrField);
    assertFalse(result.isStaticsOK());
    assertFalse(result.isValidResult());
  }

  @Test
  public void attribute_in_call() {
    resolveTest("new A().@<caret>instanceField()", GrField.class);
  }

  @Test
  public void spread_attribute() {
    referenceExpressionTest("[new A()]*.@instanceField", GrField.class, "java.util.ArrayList<java.lang.String>");
  }

  @Test
  public void spread_attribute_deep() {
    referenceExpressionTest("[[new A()]]*.@instanceField", null, "java.util.List");
  }

  @Test
  public void spread_static_attribute() {
    referenceExpressionTest("[A]*.@staticField", GrField.class, "java.util.ArrayList<java.lang.String>");
  }
}
