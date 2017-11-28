/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

public class CommentInCustomFileTypesTest extends LightPlatformCodeInsightTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  public void testBlockComment() {
    configureByFile("/codeInsight/commentInCustomFileType/block1.cs");
    performBlockCommentAction();
    checkResultByFile("/codeInsight/commentInCustomFileType/block1_after.cs");

    configureByFile("/codeInsight/commentInCustomFileType/block1_after.cs");
    performBlockCommentAction();
    checkResultByFile("/codeInsight/commentInCustomFileType/block1_after2.cs");
  }

  public void testLineComment() {
    configureByFile("/codeInsight/commentInCustomFileType/line1.cs");
    performLineCommentAction();
    checkResultByFile("/codeInsight/commentInCustomFileType/line1_after.cs");

    configureByFile("/codeInsight/commentInCustomFileType/line2.cs");
    performLineCommentAction();
    checkResultByFile("/codeInsight/commentInCustomFileType/line2_after.cs");
  }

  private static void performBlockCommentAction() {
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_BLOCK);
  }

  private static void performLineCommentAction() {
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);
  }
}
