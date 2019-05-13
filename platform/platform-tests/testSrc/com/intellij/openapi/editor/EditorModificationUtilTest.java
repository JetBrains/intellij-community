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
package com.intellij.openapi.editor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class EditorModificationUtilTest extends LightPlatformCodeInsightTestCase {
  public void testInsertStringAtCaretNotMovingCaret() {
    configureFromFileText(getTestName(false) + ".txt", "text <caret>");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      EditorModificationUtil.insertStringAtCaret(myEditor, " ", false, false);
    });

    checkResultByText("text <caret> ");
  }
}
