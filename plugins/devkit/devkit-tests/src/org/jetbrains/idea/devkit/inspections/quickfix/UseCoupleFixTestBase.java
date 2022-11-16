// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.inspections.internal.UseCoupleInspection;

public abstract class UseCoupleFixTestBase extends DevKitInspectionFixTestBase {

  protected static final String CONVERT_TO_COUPLE_OF_FIX_NAME = "Replace with 'Couple.of()'";
  protected static final String CONVERT_TO_COUPLE_TYPE_FIX_NAME = "Replace with 'Couple<String>'";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseCoupleInspection());
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
    moduleBuilder.addLibrary("platform-util-rt", PathUtil.getJarPathForClass(Pair.class));
  }
}
