// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ExpressionTest;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMapProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class CustomMapPropertyTest implements ExpressionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @BeforeEach
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