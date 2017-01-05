/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

public class StringConcatenationInLoopsInspectionFixTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    // Instantiation via LocalInspectionEP is necessary as tool ID lacks ending -s (shortName is StringConcatenationInLoop)
    // which results in error when registering suppress actions
    LocalInspectionEP ep = new LocalInspectionEP();
    ep.id = "StringConcatenationInLoop";
    ep.implementationClass = StringConcatenationInLoopsInspection.class.getName();
    LocalInspectionTool tool = (LocalInspectionTool)ep.instantiateTool();

    return new LocalInspectionTool[]{tool};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/stringConcatInLoop";
  }
}