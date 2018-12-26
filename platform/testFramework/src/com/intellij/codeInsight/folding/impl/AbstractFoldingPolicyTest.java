/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractFoldingPolicyTest extends LightPlatformCodeInsightFixtureTestCase {
  protected void doTest(@NotNull String text, @NotNull String ext) {
    myFixture.configureByText("test." + ext, text);

    CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(myFixture.getEditor());
    EditorFoldingInfo info = EditorFoldingInfo.get(myFixture.getEditor());
    FoldRegion[] foldRegions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
    assertTrue(foldRegions.length > 0);
    for (FoldRegion region : foldRegions) {
      PsiElement element = info.getPsiElement(region);
      if (element == null) {
        continue;
      }
      String signature = FoldingPolicy.getSignature(element);
      assertNotNull(signature);
      assertEquals(element, FoldingPolicy.restoreBySignature(element.getContainingFile(), signature));
    }
  }

}
