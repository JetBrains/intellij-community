// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

public class InstanceIElementTypeFieldInspectionTest extends InstanceIElementTypeFieldInspectionTestBase implements
                                                                                                         ExpectedPluginModeProvider {
  @Override
  public @NotNull KotlinPluginMode getPluginMode() {
    return KotlinPluginMode.K2;
  }

  @Override
  protected void setUp() {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), () -> super.setUp());
  }

  @Override
  protected String getFileExtension() {
    return "java";
  }

  public void testInstanceFieldWithIElementType() {
    doTest();
  }

  public void testStaticFieldNoWarning() {
    doTest();
  }

  public void testSubtypeDetection() {
    doTest();
  }

  public void testQuickFix() {
    doTest("Make field 'static final'");
  }

  public void testEnumFieldsNoWarning() {
    doTest();
  }
}
