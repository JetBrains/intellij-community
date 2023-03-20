// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.editorActions.moveUpDown.MoveStatementDownAction;
import com.intellij.codeInsight.editorActions.moveUpDown.MoveStatementUpAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.io.File;

public abstract class MoveStatementUpDownTestBase extends LightPlatformCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/moveStatementAction/";

  protected Runnable myBeforeMoveTask;
  protected Runnable myAfterMoveTask;

  @Override
  protected void tearDown() throws Exception {
    myBeforeMoveTask = null;
    myAfterMoveTask = null;
    super.tearDown();
  }

  protected void doTest() {
    String baseName = BASE_PATH + getTestName(true);
    String ext = findExtension();
    String fileName = baseName + "."+ext;

    try {
      String afterFileName = baseName + "_afterUp." + ext;
      EditorActionHandler handler = new MoveStatementUpAction().getHandler();
      performAction(fileName, handler, afterFileName);

      afterFileName = baseName + "_afterDown." + ext;
      handler = new MoveStatementDownAction().getHandler();
      performAction(fileName, handler, afterFileName);
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  private void performAction(String fileName, EditorActionHandler handler, String afterFileName) {
    configureByFile(fileName);
    boolean enabled = handler.isEnabled(getEditor(), null, null);
    File file = new File(getTestDataPath(), afterFileName);
    if (!file.exists()) afterFileName = fileName;
    if (myBeforeMoveTask != null) {
      myBeforeMoveTask.run();
    }
    if (enabled) {
      WriteCommandAction.runWriteCommandAction(null, () -> handler.execute(getEditor(), null, null));
    }
    checkResultByFile(afterFileName);
    if (myAfterMoveTask != null) {
      myAfterMoveTask.run();
    }
  }

  private String findExtension() {
    File[] files = new File(getTestDataPath() + BASE_PATH).listFiles((dir, name) -> StringUtil.startsWithConcatenation(name, getTestName(true), "."));
    return FileTypeManagerEx.getInstanceEx().getExtension(files[0].getName());
  }
}
