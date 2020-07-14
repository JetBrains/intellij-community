// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.idea.HardwareAgentRequired;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@HardwareAgentRequired
public class AntHighlightingPerformanceTest extends DaemonAnalyzerTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/highlighting/";
  }

  public void testBigFilePerformance() throws IOException {
    configureByFiles(null,
                     findVirtualFile(getTestName(false) + ".xml"),
                     findVirtualFile("buildserver.xml"),
                     findVirtualFile("buildserver.properties"));
    PlatformTestUtil.startPerformanceTest("Big ant file highlighting", 15_000, () -> doDoTest(true, false))
      .setup(getPsiManager()::dropPsiCaches)
      .assertTiming();
  }

  @NotNull
  @Override
  protected List<HighlightInfo> doHighlighting() {
    return Collections.emptyList();
  }
}
