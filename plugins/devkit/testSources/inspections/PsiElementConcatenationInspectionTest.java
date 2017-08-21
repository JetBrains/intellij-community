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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PsiElementConcatenationInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void beforeActionStarted(String testName, String contents) {
    try {
      createAndSaveFile("com/intellij/psi/PsiElement.java",
                        "package com.intellij.psi;interface PsiElement {}");
      createAndSaveFile("com/intellij/psi/PsiExpression.java",
                        "package com.intellij.psi;interface PsiExpression extends PsiElement {}");
      createAndSaveFile("com/intellij/psi/PsiType.java",
                        "package com.intellij.psi;interface PsiType {}");
      createAndSaveFile("com/intellij/psi/PsiElementFactory.java",
                        "package com.intellij.psi;\n" +
                        "interface PsiElementFactory {\n" +
                        "PsiExpression createExpressionFromText(String str, PsiElement context);\n" +
                        "}");
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    super.beforeActionStarted(testName, contents);
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new PsiElementConcatenationInspection()};
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("devkit") + "/testData";
  }

  public void test() {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/inspections/psiElementConcatenation";
  }
}
