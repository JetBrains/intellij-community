// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.editorActions.UnSelectWordHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.common.EditorCaretTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Select word checks for a JUnit 5 test.
 * This is the replacement of {@link SelectWordTestBase}.
 */
@TestOnly
public final class SelectWordTestUtil {
  private SelectWordTestUtil() {
  }

  /**
   * Runs {@link #doTest(CodeInsightTestFixture, String, String, boolean, boolean)} with unselect and without default folding.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture, @NotNull String testMethodName, @NonNls @NotNull String ext) {
    doTest(fixture, testMethodName, ext, true);
  }

  /**
   * Runs {@link #doTest(CodeInsightTestFixture, String, String, boolean, boolean)} without default folding.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull String testMethodName,
                            @NonNls @NotNull String ext,
                            boolean unselect) {
    doTest(fixture, testMethodName, ext, unselect, false);
  }

  /**
   * Configures {@code codeInsight/selectWordAction/<name>/before.<ext>} from the test data path of {@code fixture}.
   * Then runs the select word action and checks the result against {@code after1.<ext>}, {@code after2.<ext>}, and so on.
   * Each check ignores the trailing spaces, as {@link SelectWordTestBase} did.
   * The test method name gives {@code name}. For example, {@code testHtml1} gives {@code html1}.
   * When {@code unselect} is {@code true}, the unselect word action then runs back and checks each earlier {@code after} file.
   * When {@code defaultFolding} is {@code true}, the initial foldings are built before the first action.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull String testMethodName,
                            @NonNls @NotNull String ext,
                            boolean unselect,
                            boolean defaultFolding) {
    @NonNls String path = "codeInsight/selectWordAction/" + PlatformTestUtil.getTestName(testMethodName, true);
    fixture.configureByFile(path + "/before." + ext);
    Editor editor = fixture.getEditor();
    if (defaultFolding) {
      EdtTestUtil.runInEdtAndWait(() -> {
        // caret might be in the way so folding will not be performed
        int offset = editor.getCaretModel().getOffset();
        editor.getCaretModel().moveToOffset(0);
        EditorTestUtil.buildInitialFoldingsInBackground(editor);
        editor.getCaretModel().moveToOffset(offset);
      });
    }
    int i = 1;
    while (true) {
      @NonNls String resultPath = path + "/after" + i + "." + ext;
      if (new File(fixture.getTestDataPath(), resultPath).exists()) {
        EdtTestUtil.runInEdtAndWait(() -> executeHandler(getHandler(), editor));
        checkResultByFile(fixture, resultPath);
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
      EdtTestUtil.runInEdtAndWait(() -> executeHandler(new UnSelectWordHandler(null), editor));
      checkResultByFile(fixture, path + "/after" + i + "." + ext);
    }
  }

  private static void checkResultByFile(@NotNull CodeInsightTestFixture fixture, @NotNull String expectedFilePath) {
    Project project = fixture.getProject();
    Editor editor = fixture.getEditor();
    String fullPath = fixture.getTestDataPath() + "/" + expectedFilePath;
    String expectedFileText;
    try {
      expectedFileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(fullPath), StandardCharsets.UTF_8));
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    WriteCommandAction.runWriteCommandAction(project, () -> {
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      TrailingSpacesStripper.strip(editor.getDocument(), false, true);
      EditorUtil.fillVirtualSpaceUntilCaret(editor);
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      Document document = EditorFactory.getInstance().createDocument(expectedFileText);
      ((DocumentImpl)document).stripTrailingSpaces(project);
      EditorCaretTestUtil.CaretAndSelectionState carets = EditorTestUtil.extractCaretAndSelectionMarkers(document);
      String expectedText = document.getText();
      String actualText = fixture.getFile().getText();
      if (!expectedText.equals(actualText)) {
        throw new FileComparisonFailedError(expectedFilePath, expectedText, actualText, fullPath);
      }
      EditorTestUtil.verifyCaretAndSelectionState(editor, carets, expectedFilePath, fullPath);
    });
  }

  private static void executeHandler(@NotNull EditorActionHandler handler, @NotNull Editor editor) {
    handler.execute(editor, null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
  }

  private static EditorActionHandler getHandler() {
    EditorAction action = (EditorAction)ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    return action.getHandler();
  }
}
