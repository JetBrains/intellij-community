// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightSubFixture;

@StressTestApplication
@PerformanceUnitTest
public class AntHighlightingPerformanceTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = heavyJavaCodeInsightSubFixture(
    "antHighlightingPerformanceFixture", null,
    context -> new DaemonAnalyzerFixture(context, getTestDataPath(), LocalInspectionTool.EMPTY_ARRAY) {
      @Override
      protected void doCheckResult(@NotNull ExpectedHighlightingData data, @NotNull Collection<? extends HighlightInfo> infos, @NotNull String text) {
        // ignore warnings
      }
    });

  private static @NotNull String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/highlighting/";
  }

  @Test
  public void testBigFilePerformance() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null,
                             fixture.findVirtualFile(fixture.getTestName(false) + ".xml"),
                             fixture.findVirtualFile("buildserver.xml"),
                             fixture.findVirtualFile("buildserver.properties"));
    Benchmark.newBenchmark("Big ant file highlighting", () -> fixture.doDoTest(true, false))
      .setup(() -> EdtTestUtil.runInEdtAndWait(PsiManager.getInstance(fixture.getProject())::dropPsiCaches))
      .start();
  }
}
