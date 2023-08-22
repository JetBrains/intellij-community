/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class StripTestDataMarkup extends ActionOnFile {
  private static final String[] MARKUP = {"<caret>", "<ref>", "<selection>", "</selection>"};

  public StripTestDataMarkup(@NotNull PsiFile file) {
    super(file);
  }

  @Override
  public void performCommand(@NotNull Environment env) {
    env.logMessage(toString());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      Document document = getDocument();
      try {
        new ExpectedHighlightingData(document, true, true, true, true).init();
      }
      catch (AssertionError ignore) {
        // can fail on unpaired markups
      }
      for (String s : MARKUP) {
        removeMarkup(document, s);
      }
    });
    PsiTestUtil.checkPsiStructureWithCommit(getFile(), PsiTestUtil::checkStubsMatchText);
  }

  private static void removeMarkup(Document document, String marker) {
    document.setText(StringUtil.replace(document.getText(), marker, ""));
  }

  @Override
  public String toString() {
    return "Strip markup: " + Arrays.toString(MARKUP);
  }
}
