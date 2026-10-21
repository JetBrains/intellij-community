// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Formatter checks for a JUnit 5 test.
 * This is the JUnit 5 replacement of {@link FormatterTestCase}.
 * <p>
 * Use the project of a {@code codeInsightModuleFixture}.
 * The fixture gives each test temporary code style settings, as {@link FormatterTestCase} did.
 * A {@code testDataDir} parameter is the test data path and the base path of {@link FormatterTestCase}, joined with a separator.
 * A {@code testName} parameter is {@code PlatformTestUtil.getTestName(testMethodName, true)}, as {@code getTestName(true)} gave.
 */
@TestOnly
public final class FormatterTestUtil {
  private FormatterTestUtil() {
  }

  /**
   * Checks that the project uses the application code style, as {@link FormatterTestCase#setUp} did.
   * Call it from any thread, for example in {@code @BeforeEach}.
   */
  public static void assertNoProjectCodeStyle(@NotNull Project project) {
    assertFalse(CodeStyle.usesOwnSettings(project));
  }

  /**
   * Formats {@code <testName>.<fileExtension>} and compares the result with {@code <testName>_after.<fileExtension>}.
   * Both files are in {@code testDataDir}.
   * When {@code resultNumber} is not {@code null}, the expected text is the {@code <<<resultNumber>>>} section of the second file.
   * Call it from any thread.
   */
  public static void doTest(@NotNull Project project,
                            @NotNull String testDataDir,
                            @NotNull String fileExtension,
                            @NotNull String testName,
                            @Nullable String resultNumber) throws IOException {
    doTestByFileNames(project, testDataDir, fileExtension,
                      testName + "." + fileExtension, testName + "_after." + fileExtension, resultNumber);
  }

  /**
   * Runs {@link #doTest(Project, String, String, String, String)} with no result number.
   */
  public static void doTest(@NotNull Project project,
                            @NotNull String testDataDir,
                            @NotNull String fileExtension,
                            @NotNull String testName) throws IOException {
    doTest(project, testDataDir, fileExtension, testName, null);
  }

  /**
   * Formats the file {@code fileNameBefore} and compares the result with the file {@code fileNameAfter}.
   * Both names include the extension, and both files are in {@code testDataDir}.
   * See {@link #loadFile} for {@code resultNumber}.
   * Call it from any thread.
   */
  public static void doTestByFileNames(@NotNull Project project,
                                       @NotNull String testDataDir,
                                       @NotNull String fileExtension,
                                       @NotNull String fileNameBefore,
                                       @NotNull String fileNameAfter,
                                       @Nullable String resultNumber) throws IOException {
    doTextTest(project, fileExtension, loadFile(testDataDir, fileNameBefore, null), loadFile(testDataDir, fileNameAfter, resultNumber));
  }

  /**
   * Creates the file {@code before.<fileExtension>} with {@code text}, formats the full file, and compares the result with {@code textAfter}.
   * Call it from any thread.
   */
  public static void doTextTest(@NotNull Project project, @NotNull String fileExtension, @NotNull String text, @NotNull String textAfter) {
    PsiFile file = createFile(project, "before." + fileExtension, text);
    checkDocument(project, file, text, textAfter);
  }

  /**
   * Creates a non-physical file, as {@code FormatterTestCase.createFileFromText} did.
   * The file type comes from {@code fileName}.
   * Call it from any thread.
   */
  public static @NotNull PsiFile createFile(@NotNull Project project, @NotNull String fileName, @NotNull String text) {
    return createFile(project, fileName, FileTypeManager.getInstance().getFileTypeByFileName(fileName), text);
  }

  /**
   * Creates a non-physical file of {@code fileType}, as {@code FormatterTestCase.createFileFromText} did with a {@code getFileType} override.
   * Call it from any thread.
   */
  public static @NotNull PsiFile createFile(@NotNull Project project, @NotNull String fileName, @NotNull FileType fileType, @NotNull String text) {
    return ReadAction.computeBlocking(() -> PsiFileFactory.getInstance(project).createFileFromText(
      fileName, fileType, text, LocalTimeCounter.currentTime(), true, false));
  }

  /**
   * Puts {@code text} into the document of {@code file}, formats the full file with {@link CodeStyleManager#reformatText},
   * and compares the document and the file with {@code textAfter}.
   * Call it from any thread.
   */
  public static void checkDocument(@NotNull Project project, @NotNull PsiFile file, @NotNull String text, @NotNull String textAfter) {
    checkDocument(project, file, text, textAfter, null);
  }

  /**
   * Runs {@link #checkDocument(Project, PsiFile, String, String)}, but formats only {@code textRange} when it is not {@code null}.
   * This is the {@code myTextRange} field of {@link FormatterTestCase}.
   * Call it from any thread.
   */
  public static void checkDocument(@NotNull Project project,
                                   @NotNull PsiFile file,
                                   @NotNull String text,
                                   @NotNull String textAfter,
                                   @Nullable TextRange textRange) {
    TextRange range = textRange != null ? textRange : ReadAction.computeBlocking(() -> file.getTextRange());
    reformatAndCheck(project, file, text, textAfter,
                  f -> CodeStyleManager.getInstance(project).reformatText(f, range.getStartOffset(), range.getEndOffset()));
  }

  /**
   * Runs {@link #checkDocument(Project, PsiFile, String, String)}, but formats the full file with {@link CodeStyleManager#reformatRange}.
   * This is the {@code doReformatRangeTest} field of {@link FormatterTestCase}.
   * Call it from any thread.
   */
  public static void checkDocumentByReformatRange(@NotNull Project project,
                                                  @NotNull PsiFile file,
                                                  @NotNull String text,
                                                  @NotNull String textAfter) {
    reformatAndCheck(project, file, text, textAfter, f -> {
      TextRange range = f.getTextRange();
      CodeStyleManager.getInstance(project).reformatRange(f, range.getStartOffset(), range.getEndOffset());
    });
  }

  private static void reformatAndCheck(@NotNull Project project,
                                       @NotNull PsiFile file,
                                       @NotNull String text,
                                       @NotNull String textAfter,
                                       @NotNull Consumer<PsiFile> formatter) {
    Document document = ReadAction.computeBlocking(() -> PsiDocumentManager.getInstance(project).getDocument(file));
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(project, () -> {
      document.replaceString(0, document.getTextLength(), text);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      assertEquals(file.getText(), document.getText());
      formatter.accept(file);
    });
    String fileText = EdtTestUtil.runInEdtAndGet(() -> {
      assertEquals(textAfter, document.getText());
      PsiDocumentManager.getInstance(project).commitDocument(document);
      return file.getText();
    });
    assertEquals(textAfter, fileText);
  }

  /**
   * Formats {@code textRange} of {@code file} with {@link CodeStyleManager#reformatRange} in a write command action.
   * When {@code textRange} is {@code null}, it formats the full file with {@link CodeStyleManager#reformat}.
   * This is {@code FormatterTestCase.performFormatting}.
   * Call it from any thread.
   */
  public static void reformat(@NotNull Project project, @NotNull PsiFile file, @Nullable TextRange textRange) {
    WriteCommandAction.runWriteCommandAction(project, () -> {
      if (textRange == null) {
        CodeStyleManager.getInstance(project).reformat(file);
      }
      else {
        CodeStyleManager.getInstance(project).reformatRange(file, textRange.getStartOffset(), textRange.getEndOffset());
      }
    });
  }

  /**
   * Loads the file {@code name} from {@code testDataDir}, and converts the line separators.
   * When {@code resultNumber} is not {@code null}, it returns the trimmed text between {@code <<<resultNumber>>>} and {@code <<</resultNumber>>>}.
   * Call it from any thread.
   */
  public static @NotNull String loadFile(@NotNull String testDataDir, @NotNull String name, @Nullable String resultNumber) throws IOException {
    String text = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(testDataDir, name)));
    if (resultNumber == null) {
      return text;
    }
    String beginLine = "<<<" + resultNumber + ">>>";
    String endLine = "<<</" + resultNumber + ">>>";
    int beginPos = text.indexOf(beginLine);
    assertTrue(beginPos >= 0);
    int endPos = text.indexOf(endLine);
    assertTrue(endPos >= 0);
    return text.substring(beginPos + beginLine.length(), endPos).trim();
  }

  /**
   * Formats each {@code *.<fileExtension>} file in {@code <testDataDir>/sanity}, and fails when the formatter throws for a file.
   * With {@code formatWithPsi}, it formats with {@link CodeStyleManager#reformat}, and otherwise with {@link CodeStyleManager#reformatText}.
   * This is {@code FormatterTestCase.doSanityTest}.
   * Call it from any thread.
   */
  public static void doSanityTest(@NotNull Project project, @NotNull String testDataDir, @NotNull String fileExtension, boolean formatWithPsi)
    throws IOException {
    File[] subFiles = new File(testDataDir, "sanity").listFiles();
    if (subFiles == null) {
      return;
    }
    List<File> failedFiles = new ArrayList<>();
    for (File subFile : subFiles) {
      if (subFile.isFile() && subFile.getName().endsWith(fileExtension)) {
        String text = new String(FileUtil.loadFileBytes(subFile), StandardCharsets.UTF_8);
        PsiFile file = createFile(project, "before." + fileExtension, StringUtil.convertLineSeparators(text));
        try {
          WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
              if (formatWithPsi) {
                CodeStyleManager.getInstance(project).reformat(file);
              }
              else {
                CodeStyleManager.getInstance(project).reformatText(file, 0, file.getTextLength());
              }
            }
            catch (Throwable e) {
              //noinspection CallToPrintStackTrace
              e.printStackTrace();
              failedFiles.add(subFile);
            }
            //noinspection UseOfSystemOutOrSystemErr
            System.out.println(subFile.getPath() + ": finished");
          });
        }
        finally {
          VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            EdtTestUtil.runInEdtAndWait(() -> {
              ((UndoManagerImpl)UndoManager.getInstance(project)).clearUndoRedoQueueInTests(virtualFile);
              ((UndoManagerImpl)UndoManager.getGlobalInstance()).clearUndoRedoQueueInTests(virtualFile);
            });
          }
        }
      }
    }
    if (!failedFiles.isEmpty()) {
      fail("Failed for files: " + StringUtil.join(failedFiles, File::getPath, "\n"));
    }
  }
}
