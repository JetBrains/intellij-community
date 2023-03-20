// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.internal.AbstractUseJBColorTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useJBColor")
public class KtUseJBColorInspectionTest extends AbstractUseJBColorTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useJBColor";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testUseJBColorConstructor() {
    doTest();
  }

  public void testUseJBColorConstant() {
    doTest();
  }
}
