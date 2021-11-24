// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.packaging;

import com.intellij.codeInspection.EmptyDirectoryInspection;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.testFramework.InspectionFixtureTestCase;

import java.io.File;

/**
 * @author Bas Leijdekkers
 */
public class EmptyDirectoryInspectionTest extends InspectionFixtureTestCase {

  public void testEmptyDirectory() {
    final String path = "com/siyeh/igtest/packaging/empty_directory/";
    new File(getTestDataPath() + path + "src/").mkdir();
    new File(getTestDataPath() + path + "src/simple/").mkdir();
    doTest(path, new GlobalInspectionToolWrapper(new EmptyDirectoryInspection()));
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }
}
