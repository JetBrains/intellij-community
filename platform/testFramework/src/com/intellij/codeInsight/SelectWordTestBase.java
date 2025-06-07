// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.editorActions.UnSelectWordHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public abstract class SelectWordTestBase extends LightPlatformCodeInsightTestCase {
  protected void doTest(@NonNls String ext) { doTest(ext, true); }

  protected void doTest(@NonNls String ext, boolean unselect) { doTest(ext, unselect, false); }

  protected void doTest(@NonNls String ext, boolean unselect, boolean defaultFolding) {
    final @NonNls String path = "/codeInsight/selectWordAction/" + getTestName(true);
    configureByFile(path + "/before." + ext);
    if (defaultFolding) {
      EditorTestUtil.buildInitialFoldingsInBackground(getEditor());
    }
    int i = 1;
    while (true) {
      final String fileName = "/after" + i + "." + ext;
      @NonNls String resultPath = path + fileName;
      if (new File(getTestDataPath() + resultPath).exists()) {
        getHandler().execute(getEditor(), null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
        checkResultByFile(fileName, resultPath, true);
        i++;
      }
      else {
        break;
      }
    }
    assertTrue(i > 1);
    if (!unselect) {
      return;
    }
    i--;
    while (true) {
      i--;
      if (i == 0) {
        break;
      }
      final String fileName = "/after" + i + "." + ext;
      new UnSelectWordHandler(null).execute(getEditor(), null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
      checkResultByFile(fileName, path + fileName, true);
    }
  }

  private static EditorActionHandler getHandler() {
    EditorAction action = (EditorAction)ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    return action.getHandler();
  }
}
