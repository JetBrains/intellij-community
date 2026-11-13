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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMapProperty;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_META_CLASS;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class MapPropertyTest implements ExpressionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void arbitraryPropertyInInstanceContext() {
    referenceExpressionTest("new HashMap<String, Integer>().foo", GroovyMapProperty.class, JAVA_LANG_INTEGER);
  }

  @Test
  public void arbitraryPropertyInStaticContext() {
    referenceExpressionTest("HashMap.foo", null, null);
  }

  @Test
  public void classPropertyInInstanceContext() {
    referenceExpressionTest("new HashMap<String, Integer>().class", GroovyMapProperty.class, JAVA_LANG_INTEGER);
  }

  @Test
  public void classPropertyInRawInstanceContext() {
    referenceExpressionTest("new HashMap().class", GroovyMapProperty.class, JAVA_LANG_OBJECT);
  }

  @Test
  public void classPropertyInStaticContext() {
    referenceExpressionTest("HashMap.class", null, "java.lang.Class<java.util.HashMap>");
  }

  @Test
  public void metaClassPropertyInInstanceContext() {
    referenceExpressionTest("new HashMap().metaClass", GroovyMapProperty.class, JAVA_LANG_OBJECT);
  }

  @Test
  public void metaClassPropertyInStaticContext() {
    referenceExpressionTest("HashMap.metaClass", GrGdkMethod.class, GROOVY_LANG_META_CLASS);
  }

  @Test
  public void classPropertyOnEmptyMapLiteral() {
    referenceExpressionTest("[:].class", GroovyMapProperty.class, JAVA_LANG_OBJECT);
  }

  @Test
  public void classPropertyOnMapLiteral() {
    referenceExpressionTest("[class : 1].class", GroovyMapProperty.class, JAVA_LANG_INTEGER);
  }
}
