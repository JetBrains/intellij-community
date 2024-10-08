// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.util.ClassUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;

public class GrClassUtilTest extends LightJavaCodeInsightFixtureTestCase {
  public void testFindClassByName() {
    myFixture.configureByText("a.groovy", """
            public class InnerClasses {
              static class Bar { }
              static class Bar$ {
                static class $Foo { }
              }
            }\
      """.stripIndent());

    TestCase.assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "InnerClasses$Bar"));
    TestCase.assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "InnerClasses$Bar$"));
    TestCase.assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "InnerClasses$Bar$$$Foo"));
  }
}
