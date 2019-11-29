// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ReturnNullInspectionTest extends LightJavaInspectionTestCase {

  public void testReturnNull() {
    final NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
    nnnManager.setNullables("com.siyeh.igtest.bugs.Nullable");
    Disposer.register(myFixture.getTestRootDisposable(), nnnManager::setNullables);
    doTest();
  }

  public void testReturnNullFromNotNullAnnotatedMethods() {
    final NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
    nnnManager.setNotNulls("com.siyeh.igtest.bugs.NotNull");
    Disposer.register(myFixture.getTestRootDisposable(), nnnManager::setNotNulls);
    doTest();
  }

  public void testWarnOptional() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final String testCaseName = getTestName(false);
    final ReturnNullInspection inspection = new ReturnNullInspection();
    if ("ReturnNullFromNotNullAnnotatedMethods".equals(testCaseName)) {
      inspection.m_ignorePrivateMethods = true;
      inspection.m_reportObjectMethods = false;
      inspection.m_reportArrayMethods = false;
      inspection.m_reportCollectionMethods = false;
    } else {
      inspection.m_reportObjectMethods = !"WarnOptional".equals(testCaseName);
      inspection.m_ignorePrivateMethods = "WarnOptional".equals(testCaseName);
    }

    return inspection;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package com.siyeh.igtest.bugs;\n" +
      "import java.lang.annotation.*;\n" +
      "@Retention(RetentionPolicy.SOURCE)\n" +
      "@Target({ElementType.TYPE_USE, ElementType.METHOD})\n" +
      "public @interface Nullable {}",

      "package com.siyeh.igtest.bugs;\n" +
      "import java.lang.annotation.*;\n" +
      "@Retention(RetentionPolicy.SOURCE)\n" +
      "@Target({ElementType.TYPE_USE, ElementType.METHOD})\n" +
      "public @interface NotNull {}"
    };
  }
}