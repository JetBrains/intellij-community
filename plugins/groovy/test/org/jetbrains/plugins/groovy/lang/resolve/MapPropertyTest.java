// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ExpressionTest;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
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
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_META_CLASS;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class MapPropertyTest implements ExpressionTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void arbitraryPropertyInInstanceContext() {
    runInEdtAndWait(() -> referenceExpressionTest("new HashMap<String, Integer>().foo", GroovyMapProperty.class, JAVA_LANG_INTEGER));
  }

  @Test
  public void arbitraryPropertyInStaticContext() {
    runInEdtAndWait(() -> referenceExpressionTest("HashMap.foo", null, null));
  }

  @Test
  public void classPropertyInInstanceContext() {
    runInEdtAndWait(() -> referenceExpressionTest("new HashMap<String, Integer>().class", GroovyMapProperty.class, JAVA_LANG_INTEGER));
  }

  @Test
  public void classPropertyInRawInstanceContext() {
    runInEdtAndWait(() -> referenceExpressionTest("new HashMap().class", GroovyMapProperty.class, JAVA_LANG_OBJECT));
  }

  @Test
  public void classPropertyInStaticContext() {
    runInEdtAndWait(() -> referenceExpressionTest("HashMap.class", null, "java.lang.Class<java.util.HashMap>"));
  }

  @Test
  public void metaClassPropertyInInstanceContext() {
    runInEdtAndWait(() -> referenceExpressionTest("new HashMap().metaClass", GroovyMapProperty.class, JAVA_LANG_OBJECT));
  }

  @Test
  public void metaClassPropertyInStaticContext() {
    runInEdtAndWait(() -> referenceExpressionTest("HashMap.metaClass", GrGdkMethod.class, GROOVY_LANG_META_CLASS));
  }

  @Test
  public void classPropertyOnEmptyMapLiteral() {
    runInEdtAndWait(() -> referenceExpressionTest("[:].class", GroovyMapProperty.class, JAVA_LANG_OBJECT));
  }

  @Test
  public void classPropertyOnMapLiteral() {
    runInEdtAndWait(() -> referenceExpressionTest("[class : 1].class", GroovyMapProperty.class, JAVA_LANG_INTEGER));
  }
}
