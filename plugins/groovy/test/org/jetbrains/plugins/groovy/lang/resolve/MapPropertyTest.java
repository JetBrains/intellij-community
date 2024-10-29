// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMapProperty;
import org.jetbrains.plugins.groovy.util.ExpressionTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_META_CLASS;

public class MapPropertyTest extends GroovyLatestTest implements ExpressionTest {

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
