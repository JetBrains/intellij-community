package com.intellij.codeInsight;

import com.intellij.codeInsight.editorActions.UnSelectWordHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SelectWordWithoutPSITest extends LightPlatformCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  public void testTest1() {
    doTest();
  }

  public void testCamelHumps() {
    EditorSettingsExternalizable.getInstance().setCamelWords(true);
    try {
      doTest();
    }
    finally {
      EditorSettingsExternalizable.getInstance().setCamelWords(false);
    }
  }

  private void doTest() {
    doTest(false); // UnselectWordAtCaretAction is not implemented
  }

  private void doTest(boolean unselect) {
    @NonNls final String path = "/codeInsight/selectWordWithoutPSIAction/" + getTestName(true);
    final Editor editor = configureByFileWithoutPSI(path + "/before");
    try {
      int i = 1;
      while (true) {
        final String fileName = "/after" + i;
        @NonNls String resultPath = path + fileName;
        if (new File(getTestDataPath() + resultPath).exists()) {
          getHandler().execute(editor, null, DataManager.getInstance().getDataContext());
          checkResultByFile(fileName, editor, resultPath);
          i++;
        }
        else {
          break;
        }
      }
      assertTrue(i > 1);

      if (!unselect) return;

      i--;
      while (true) {
        i--;
        if (i == 0) {
          break;
        }
        final String fileName = "/after" + i;
        new UnSelectWordHandler(null).execute(editor, null, DataManager.getInstance().getDataContext());
        checkResultByFile(fileName, editor, path + fileName);
      }
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  @NotNull
  private Editor configureByFileWithoutPSI(@TestDataFile @NonNls @NotNull String filePath) {
    try {
      final File ioFile = new File(getTestDataPath() + filePath);
      String fileText = FileUtilRt.loadFile(ioFile, CharsetToolkit.UTF8, true);
      return configureFromFileTextWithoutPSI(fileText);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void checkResultByFile(@Nullable String message, @NotNull Editor editor, @TestDataFile @NotNull String filePath) {
    try {
      final File ioFile = new File(getTestDataPath() + filePath);
      String fileText = FileUtil.loadFile(ioFile, StandardCharsets.UTF_8);
      checkResultByTextWithoutPSI(message, editor, StringUtil.convertLineSeparators(fileText), false, ioFile.getPath());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static EditorActionHandler getHandler() {
    EditorAction action = (EditorAction)ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    return action.getHandler();
  }
}
