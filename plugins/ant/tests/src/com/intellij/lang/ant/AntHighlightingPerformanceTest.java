// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;

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
    Benchmark.newBenchmark("Big ant file highlighting", () -> doDoTest(true, false))
      .setup(getPsiManager()::dropPsiCaches)
      .start();
  }

  @Override
  protected void doCheckResult(@NotNull ExpectedHighlightingData data, @NotNull Collection<? extends HighlightInfo> infos, @NotNull String text) {
    // ignore warnings
  }
}
