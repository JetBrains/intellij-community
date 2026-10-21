// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellchecker.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Path;

public abstract class SpellcheckerInspectionTestCase extends BasePlatformTestCase {

  @Override
  protected boolean isCommunity() {
    return true;
  }

  protected static String getSpellcheckerTestDataPath() {
    return "/spellchecker/testData/";
  }

  @Override
  protected String getTestDataPath() {
    var basePath = getBasePath();
    if (BazelTestUtil.isUnderBazelTest() && !basePath.isEmpty()) {
      var label = BazelLabel.Companion.fromString("@community//spellchecker:testData");
      var relativePath = Path.of(getSpellcheckerTestDataPath()).relativize(Path.of(basePath));
      return BazelTestUtil.getFileFromBazelRuntime(label).resolve(relativePath).toString();
    }
    return super.getTestDataPath();
  }

  protected void doTest(String file) {
    myFixture.enableInspections(getInspectionTools());
    myFixture.testHighlighting(false, false, true, file);
  }

  public static LocalInspectionTool[] getInspectionTools() {
    return new LocalInspectionTool[]{new GrazieSpellCheckingInspection()};
  }
}
