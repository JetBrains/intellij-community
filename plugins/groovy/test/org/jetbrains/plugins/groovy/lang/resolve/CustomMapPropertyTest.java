// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMapProperty;
import org.jetbrains.plugins.groovy.util.ExpressionTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Before;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;

public class CustomMapPropertyTest extends GroovyLatestTest implements ExpressionTest {

  @Before
  public void addClasses() {
    getFixture().addFileToProject("classes.groovy", """
      class Pojo { def pojoProperty }
      
      class SomeMapClass extends HashMap<String, Pojo> {
          public static final CONSTANT = 1
          static class Inner {
              public static final INNER_CONSTANT = 4
          }
      }
      """);
  }

  @Test
  public void constantInInstanceContext() {
    referenceExpressionTest("new SomeMapClass().CONSTANT", GroovyMapProperty.class, "Pojo");
  }

  @Test
  public void constantInStaticContext() {
    referenceExpressionTest("SomeMapClass.CONSTANT", GrField.class, JAVA_LANG_INTEGER);
  }

  @Test
  public void innerClassInInstanceContext() {
    referenceExpressionTest("new SomeMapClass().Inner", GroovyMapProperty.class, "Pojo");
  }

  @Test
  public void innerClassInStaticContext() {
    referenceExpressionTest("SomeMapClass.Inner", GrClassDefinition.class, "java.lang.Class<SomeMapClass.Inner>");
  }

  @Test
  public void innerPropertyInInstanceContext() {
    resolveTest("new SomeMapClass().Inner.<caret>pojoProperty", GrAccessorMethod.class);
  }

  @Test
  public void innerPropertyInStaticContext() {
    resolveTest("SomeMapClass.Inner.<caret>INNER_CONSTANT", GrField.class);
  }
}