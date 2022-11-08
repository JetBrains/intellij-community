// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.inspections.internal.UseVirtualFileEqualsInspection;

public abstract class UseVirtualFileEqualsFixTestBase extends UseEqualsFixTestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseVirtualFileEqualsInspection());
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.addLibrary("platform-core-api", PathUtil.getJarPathForClass(VirtualFile.class));
  }
}
