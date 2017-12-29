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
package com.intellij.psi.formatter;

import com.intellij.lang.Language;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class FormatterTestCase extends LightPlatformTestCase {
  protected boolean doReformatRangeTest;
  protected TextRange myTextRange;
  protected EditorImpl myEditor;
  protected PsiFile myFile;

  enum CheckPolicy {

    PSI(true, false),DOCUMENT(false, true),BOTH(true, true);

    private final boolean myCheckPsi;
    private final boolean myCheckDocument;

    private CheckPolicy(boolean checkPsi, boolean checkDocument) {
      myCheckDocument = checkDocument;
      myCheckPsi = checkPsi;
    }

    public boolean isCheckPsi() {
      return myCheckPsi;
    }

    public boolean isCheckDocument() {
      return myCheckDocument;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    assertFalse(CodeStyleSettingsManager.getInstance(getProject()).USE_PER_PROJECT_SETTINGS);
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

  protected void doTextTest(@NonNls final String text, @NonNls final String textAfter) throws IncorrectOperationException {
    doTextTest(text, textAfter, CheckPolicy.BOTH);
  }

  protected void doTextTest(final String text, final String textAfter, @NotNull CheckPolicy checkPolicy)
    throws IncorrectOperationException {
    final String fileName = "before." + getFileExtension();
    final PsiFile file = createFileFromText(text, fileName, PsiFileFactory.getInstance(getProject()));

    if (checkPolicy.isCheckDocument()) {
      checkDocument(file, text, textAfter);
    }

    if (checkPolicy.isCheckPsi()) {
      /*
      restoreFileContent(file, text);

      checkPsi(file, textAfter);
      */
    }


  }

  protected PsiFile createFileFromText(String text, String fileName, final PsiFileFactory fileFactory) {
    return fileFactory.createFileFromText(fileName, getFileType(fileName), text, LocalTimeCounter.currentTime(), true, false);
  }

  protected FileType getFileType(String fileName) {
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName);
  }


  @Override
  protected void tearDown() throws Exception {
    try {
      if (myFile != null) {
        ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(myFile.getVirtualFile());
        FileEditorManager.getInstance(getProject()).closeFile(myFile.getVirtualFile());
      }
    }
    finally {
      myEditor = null;
      myFile = null;
      super.tearDown();
    }
  }

  @SuppressWarnings({"UNUSED_SYMBOL"})
  private void restoreFileContent(final PsiFile file, final String text) {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      document.replaceString(0, document.getTextLength(), text);
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    }), "test", null);
  }

  protected boolean doCheckDocumentUpdate() {
    return false;
  }

  protected void checkDocument(final PsiFile file, final String text, String textAfter) {
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    final EditorImpl editor;

    if (doCheckDocumentUpdate()) {
      editor =(EditorImpl)FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file.getVirtualFile(), 0), false);
      editor.putUserData(EditorImpl.DO_DOCUMENT_UPDATE_TEST, Boolean.TRUE);
      if (myFile != null) {
        FileEditorManager.getInstance(getProject()).closeFile(myFile.getVirtualFile());
      }
      myEditor = editor;
      myFile = file;
    }
    else {
      editor = null;
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

  @SuppressWarnings({"UNUSED_SYMBOL"})
  private void checkPsi(final PsiFile file, String textAfter) {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> performFormatting(file)), "", "");


    String fileText = file.getText();
    assertEquals(textAfter, fileText);
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
  protected static CommonCodeStyleSettings getSettings(Language language) {
    return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(language);
  }

  protected CodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  protected void doSanityTestForDirectory(File directory, final boolean formatWithPsi) throws IOException, IncorrectOperationException {
    final List<File> failedFiles = new ArrayList<>();
    doSanityTestForDirectory(directory, failedFiles, formatWithPsi);
    if (!failedFiles.isEmpty()) {
      fail("Failed for files: " + composeMessage(failedFiles));
    }
  }

  private void doSanityTestForDirectory(final File directory, final List<File> failedFiles, final boolean formatWithPsi)
    throws IOException, IncorrectOperationException {
    final File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        doSanityTestForFile(file, failedFiles, formatWithPsi);
        doSanityTestForDirectory(file, failedFiles, formatWithPsi);
      }
    }
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

  private void doSanityTestForFile(final File subFile, final List<File> failedFiles, final boolean formatWithPsi)
    throws IOException, IncorrectOperationException {
    if (subFile.isFile() && subFile.getName().endsWith(getFileExtension())) {
      final byte[] bytes = FileUtil.loadFileBytes(subFile);
      final String text = new String(bytes);
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

  private String composeMessage(final List<File> failedFiles) {
    final StringBuffer result = new StringBuffer();
    for (File file : failedFiles) {
      result.append(file.getPath());
      result.append("\n");
    }
    return result.toString();
  }
}
