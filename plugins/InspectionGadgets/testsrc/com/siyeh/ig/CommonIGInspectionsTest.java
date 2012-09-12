/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.PluginPathManager;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;
import org.jetbrains.annotations.NotNull;

public class CommonIGInspectionsTest extends LightDaemonAnalyzerTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test";
  }

  private void doTest(boolean checkWarnings, boolean checkInfos, InspectionProfileEntry... tools) throws Exception {
    for (InspectionProfileEntry tool : tools) { enableInspectionTool(tool); }
    doTest("/common/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  public void testUnnecessaryReturns() throws Exception {
    final UnnecessaryReturnInspection inspection = new UnnecessaryReturnInspection();
    inspection.ignoreInThenBranch = true;
    doTest(true, false, inspection);
  }
}
