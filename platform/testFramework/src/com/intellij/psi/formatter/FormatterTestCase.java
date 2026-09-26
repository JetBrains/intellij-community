// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import kotlin.text.Charsets;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class FormatterTestCase extends LightPlatformTestCase {
  protected boolean doReformatRangeTest;
  protected TextRange myTextRange;
  protected EditorImpl myEditor;
  protected PsiFile myFile;

  private final Disposable myBeforeParentDisposeDisposable = Disposer.newDisposable();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    assertFalse(CodeStyle.usesOwnSettings(getProject()));
  }

  protected void doTest(String resultNumber) throws Exception {
    doTestForResult(getTestName(true), resultNumber);
  }

  protected void doTest() throws Exception {
    doTest(null);
  }

  private void doTestForResult(String testName, String resultNumber) throws Exception {
    doTest(testName + "." + getFileExtension(), testName + "_after." + getFileExtension(), resultNumber);
  }

  protected void doTest(String fileNameBefore, String fileNameAfter, String resultNumber) throws Exception {
    doTextTest(loadFile(fileNameBefore, null), loadFile(fileNameAfter, resultNumber));
  }

  protected final void doTest(@NonNls String fileNameBefore, @NonNls String fileNameAfter) throws Exception {
    doTextTest(loadFile(fileNameBefore + "." + getFileExtension(), null), loadFile(fileNameAfter + "." + getFileExtension(), null));
  }

  protected void doTextTest(final String text, final String textAfter) {
    String fileName = "before." + getFileExtension();
    PsiFile file = createFileFromText(text, fileName, PsiFileFactory.getInstance(getProject()));

    checkDocument(file, text, textAfter);
  }

  protected PsiFile createFileFromText(String text, String fileName, final PsiFileFactory fileFactory) {
    return fileFactory.createFileFromText(fileName, getFileType(fileName), text, LocalTimeCounter.currentTime(), true, false);
  }

  protected FileType getFileType(String fileName) {
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName);
  }

  @Override
  public final @NotNull Disposable getTestRootDisposable() {
    return myBeforeParentDisposeDisposable;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myFile != null) {
        ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(myFile.getVirtualFile());
        FileEditorManager.getInstance(getProject()).closeFile(myFile.getVirtualFile());
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }

    myEditor = null;
    myFile = null;

    try {
      Disposer.dispose(myBeforeParentDisposeDisposable);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected boolean doCheckDocumentUpdate() {
    return false;
  }

  protected void checkDocument(final PsiFile file, final String text, String textAfter) {
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    assert document != null;
    final EditorImpl editor;

    if (doCheckDocumentUpdate()) {
      editor =(EditorImpl)FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file.getVirtualFile(), 0), false);
      assert editor != null;
      if (myFile != null) {
        FileEditorManager.getInstance(getProject()).closeFile(myFile.getVirtualFile());
      }
      myEditor = editor;
      myFile = file;
    }

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.replaceString(0, document.getTextLength(), text);
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
      assertEquals(file.getText(), document.getText());

      try {
        if (doReformatRangeTest) {
          CodeStyleManager.getInstance(getProject())
            .reformatRange(file, file.getTextRange().getStartOffset(), file.getTextRange().getEndOffset());
        }
        else if (myTextRange != null) {
          CodeStyleManager.getInstance(getProject()).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
        }
        else {
          CodeStyleManager.getInstance(getProject())
            .reformatText(file, file.getTextRange().getStartOffset(), file.getTextRange().getEndOffset());
        }
      }
      catch (IncorrectOperationException e) {
        fail();
      }
    });

    assertEquals(textAfter, document.getText());
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    assertEquals(textAfter, file.getText());
  }

  protected void performFormatting(final PsiFile file) {
    try {
      if (myTextRange == null) {
        CodeStyleManager.getInstance(getProject()).reformat(file);
      }
      else {
        CodeStyleManager.getInstance(getProject()).reformatRange(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
      }
    }
    catch (IncorrectOperationException e) {
      fail();
    }
  }

  protected void performFormattingWithDocument(final PsiFile file) {
    try {
      if (myTextRange == null) {
        CodeStyleManager.getInstance(getProject()).reformatText(file, 0, file.getTextLength());
      }
      else {
        CodeStyleManager.getInstance(getProject()).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
      }
    }
    catch (IncorrectOperationException e) {
      fail();
    }
  }

  protected String loadFile(String name, String resultNumber) throws Exception {
    String fullName = getTestDataPath() + File.separatorChar + getBasePath() + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName));
    text = StringUtil.convertLineSeparators(text);
    if (resultNumber == null) {
      return prepareText(text);
    }
    else {
      String beginLine = "<<<" + resultNumber + ">>>";
      String endLine = "<<</" + resultNumber + ">>>";
      int beginPos = text.indexOf(beginLine);
      assertTrue(beginPos >= 0);
      int endPos = text.indexOf(endLine);
      assertTrue(endPos >= 0);

      return prepareText(text.substring(beginPos + beginLine.length(), endPos).trim());

    }
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  protected String prepareText(final String text) {
    return text;
  }

  protected abstract String getBasePath();

  protected abstract String getFileExtension();

  /**
   * Returns common (spacing, blank lines etc.) settings for the given language.
   * @param language The language to search settings for.
   * @return Language common settings or root settings if the language doesn't have any common
   *         settings of its own.
   */
  protected CommonCodeStyleSettings getSettings(Language language) {
    return CodeStyle.getSettings(getProject()).getCommonSettings(language);
  }

  protected CodeStyleSettings getSettings() {
    return CodeStyle.getSettings(getProject());
  }

  protected void doSanityTest(final boolean formatWithPsi) throws IOException, IncorrectOperationException {
    final File sanityDirectory = new File(getTestDataPath() + File.separatorChar + getBasePath(), "sanity");
    final File[] subFiles = sanityDirectory.listFiles();
    final List<File> failedFiles = new ArrayList<>();

    if (subFiles != null) {
      for (final File subFile : subFiles) {
        doSanityTestForFile(subFile, failedFiles, formatWithPsi);
      }

      if (!failedFiles.isEmpty()) {
        fail("Failed for files: " + composeMessage(failedFiles));
      }
    }
  }

  private void doSanityTestForFile(final File subFile, final List<? super File> failedFiles, final boolean formatWithPsi)
    throws IOException, IncorrectOperationException {
    if (subFile.isFile() && subFile.getName().endsWith(getFileExtension())) {
      final byte[] bytes = FileUtil.loadFileBytes(subFile);
      final String text = new String(bytes, Charsets.UTF_8);
      final String fileName = "before." + getFileExtension();
      final PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, getFileType(fileName), StringUtil.convertLineSeparators(text), LocalTimeCounter.currentTime(), true);

      try {
        CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            if (formatWithPsi) {
              performFormatting(file);
            }
            else {
              performFormattingWithDocument(file);
            }
          }
          catch (Throwable e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            failedFiles.add(subFile);
          }
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println(subFile.getPath() + ": finished");
        }), "", null);
      }
      finally {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(virtualFile);
          ((UndoManagerImpl)UndoManager.getGlobalInstance()).clearUndoRedoQueueInTests(virtualFile);
        }
      }
    }
  }

  private static String composeMessage(final List<? extends File> failedFiles) {
    final StringBuilder result = new StringBuilder();
    for (File file : failedFiles) {
      result.append(file.getPath());
      result.append("\n");
    }
    return result.toString();
  }
}
