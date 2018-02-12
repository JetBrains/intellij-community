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
package com.intellij.testFramework;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class LightPlatformCodeInsightTestCase extends LightPlatformTestCase {
  protected static Editor myEditor;
  protected static PsiFile myFile;
  protected static VirtualFile myVFile;

  @Override
  protected void runTest() throws Throwable {
    if (isRunInWriteAction()) {
      WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Void, Throwable>)() -> {
        doRunTest();
        return null;
      });
    }
    else {
      new WriteCommandAction.Simple(getProject()){
        @Override
        protected void run() throws Throwable {
          doRunTest();
        }
      }.performCommand();
    }
  }

  protected void doRunTest() throws Throwable {
    super.runTest();
  }

  protected boolean isRunInWriteAction() {
    return false;
  }

  /**
   * Configure test from data file. Data file is usual java, xml or whatever file that needs to be tested except it
   * has &lt;caret&gt; marker where caret should be placed when file is loaded in editor and &lt;selection&gt;&lt;/selection&gt;
   * denoting selection bounds.
   * @param filePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   */
  protected void configureByFile(@TestDataFile @NonNls @NotNull String filePath) {
    try {
      String fullPath = getTestDataPath() + filePath;
      final File ioFile = new File(fullPath);
      checkCaseSensitiveFS(fullPath, ioFile);
      String fileText = FileUtilRt.loadFile(ioFile, CharsetToolkit.UTF8, true);
      configureFromFileText(ioFile.getName(), fileText);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void checkCaseSensitiveFS(String fullOrRelativePath, File ioFile) throws IOException {
    fullOrRelativePath = FileUtil.toSystemDependentName(FileUtil.toCanonicalPath(fullOrRelativePath));
    if (!ioFile.getCanonicalPath().endsWith(fullOrRelativePath)) {
      throw new RuntimeException("Search for: " + fullOrRelativePath + "; but found: " + ioFile.getCanonicalPath());
    }
  }

  @NonNls
  @NotNull
  protected String getTestDataPath() {
    if (myTestDataPath != null) {
      return myTestDataPath;
    }
    return PathManagerEx.getTestDataPath();
  }

  @NotNull
  protected VirtualFile getVirtualFile(@NonNls @NotNull String filePath) {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);
    return vFile;
  }

  /**
   * Same as configureByFile but text is provided directly.
   * @param fileName - name of the file.
   * @param fileText - data file text.
   */
  @NotNull
  protected static Document configureFromFileText(@NonNls @NotNull final String fileName, @NonNls @NotNull final String fileText) {
    return configureFromFileText(fileName, fileText, false);
  }

  /**
   * Same as configureByFile but text is provided directly.
   * @param fileName - name of the file.
   * @param fileText - data file text.
   * @param checkCaret - if true, if will be verified that file contains at least one caret or selection marker
   */
  @NotNull
  protected static Document configureFromFileText(@NonNls @NotNull final String fileName,
                                                  @NonNls @NotNull final String fileText,
                                                  boolean checkCaret) {
    return new WriteCommandAction<Document>(null) {
      @Override
      protected void run(@NotNull Result<Document> result) {
        final Document fakeDocument = new DocumentImpl(fileText);

        EditorTestUtil.CaretAndSelectionState caretsState = EditorTestUtil.extractCaretAndSelectionMarkers(fakeDocument);
        if(checkCaret) {
          assertTrue("No caret specified in " + fileName, caretsState.hasExplicitCaret());
        }

        String newFileText = fakeDocument.getText();
        Document document;
        try {
          document = setupFileEditorAndDocument(fileName, newFileText);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        EditorTestUtil.setCaretsAndSelection(myEditor, caretsState);
        setupEditorForInjectedLanguage();
        result.setResult(document);
      }
    }.execute().getResultObject();
  }

  @NotNull
  protected static Editor configureFromFileTextWithoutPSI(@NonNls @NotNull final String fileText) {
    return new WriteCommandAction<Editor>(getProject()) {
      @Override
      protected void run(@NotNull Result<Editor> result) {
        final Document fakeDocument = EditorFactory.getInstance().createDocument(fileText);
        EditorTestUtil.CaretAndSelectionState caretsState = EditorTestUtil.extractCaretAndSelectionMarkers(fakeDocument);

        String newFileText = fakeDocument.getText();
        Document document = EditorFactory.getInstance().createDocument(newFileText);
        final Editor editor = EditorFactory.getInstance().createEditor(document, getProject());
        ((EditorImpl)editor).setCaretActive();

        EditorTestUtil.setCaretsAndSelection(editor, caretsState);
        result.setResult(editor);
      }
    }.execute().getResultObject();
  }

  @NotNull
  protected static Editor createEditor(@NotNull VirtualFile file) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    Editor editor = FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file, 0), false);
    DaemonCodeAnalyzer.getInstance(getProject()).restart();
    assertNotNull(editor);
    ((EditorImpl)editor).setCaretActive();
    return editor;
  }

  @NotNull
  private static Document setupFileEditorAndDocument(@NotNull String fileName, @NotNull String fileText) throws IOException {
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, CharsetToolkit.UTF8_CHARSET);
    EncodingProjectManager.getInstance(ProjectManager.getInstance().getDefaultProject()).setEncoding(null, CharsetToolkit.UTF8_CHARSET);
    PostprocessReformattingAspect.getInstance(ourProject).doPostponedFormatting();
    deleteVFile();

    myEditor = createSaveAndOpenFile(fileName, fileText);
    myVFile = FileDocumentManager.getInstance().getFile(myEditor.getDocument());
    myFile = getPsiManager().findFile(myVFile);

    return myEditor.getDocument();
  }

  @NotNull
  protected static Editor createSaveAndOpenFile(@NotNull String relativePath, @NotNull String fileText) throws IOException {
    return WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Editor, IOException>)() -> {
      VirtualFile myVFile = VfsTestUtil.createFile(getSourceRoot(),relativePath);
      VfsUtil.saveText(myVFile, fileText);
      final FileDocumentManager manager = FileDocumentManager.getInstance();
      final Document document = manager.getDocument(myVFile);
      assertNotNull("Can't create document for '" + relativePath + "'", document);
      manager.reloadFromDisk(document);
      document.insertString(0, " ");
      document.deleteString(0, 1);
      PsiFile myFile = getPsiManager().findFile(myVFile);
      assertNotNull("Can't create PsiFile for '" + relativePath + "'. Unknown file type most probably.", myFile);
      assertTrue(myFile.isPhysical());
      Editor myEditor = createEditor(myVFile);
      myVFile.setCharset(CharsetToolkit.UTF8_CHARSET);

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      return myEditor;
    });
  }

  @NotNull
  protected static VirtualFile createAndSaveFile(@NotNull String relativePath, @NotNull String fileText) throws IOException {
    return WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<VirtualFile, IOException>)() -> {
      VirtualFile myVFile = VfsTestUtil.createFile(getSourceRoot(),relativePath);
      VfsUtil.saveText(myVFile, fileText);
      return myVFile;
    });
  }

  protected static void setupEditorForInjectedLanguage() {
    if (myEditor != null) {
      Editor hostEditor = myEditor instanceof EditorWindow ? ((EditorWindow)myEditor).getDelegate() : myEditor;
      PsiFile hostFile = myFile == null ? null : InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(myFile);
      final Ref<EditorWindow> editorWindowRef = new Ref<>();
      hostEditor.getCaretModel().runForEachCaret(caret -> {
        Editor editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(hostEditor, hostFile);
        if (caret == hostEditor.getCaretModel().getPrimaryCaret() && editor instanceof EditorWindow) {
          editorWindowRef.set((EditorWindow)editor);
        }
      });
      if (!editorWindowRef.isNull()) {
        myEditor = editorWindowRef.get();
        myFile = editorWindowRef.get().getInjectedFile();
      }
    }
  }

  private static void deleteVFile() throws IOException {
    if (myVFile != null) {
      WriteAction.run(() -> {
        // avoid messing with invalid files, in case someone calls configureXXX() several times
        PsiDocumentManager.getInstance(ourProject).commitAllDocuments();
        FileEditorManager.getInstance(ourProject).closeFile(myVFile);
        myVFile.delete(ourProject);
      });
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
      for (VirtualFile openFile : editorManager.getOpenFiles()) {
        editorManager.closeFile(openFile);
      }
      deleteVFile();
      myEditor = null;
      myFile = null;
      myVFile = null;
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Validates that content of the editor as well as caret and selection matches one specified in data file that
   * should be formed with the same format as one used in configureByFile
   * @param expectedFilePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   */
  protected void checkResultByFile(@TestDataFile @NonNls @NotNull String expectedFilePath) {
    checkResultByFile(null, expectedFilePath, false);
  }

  /**
   * Validates that content of the editor as well as caret and selection matches one specified in data file that
   * should be formed with the same format as one used in configureByFile
   * @param message - this check specific message. Added to text, caret position, selection checking. May be null
   * @param expectedFilePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param ignoreTrailingSpaces - whether trailing spaces in editor in data file should be stripped prior to comparing.
   */
  protected void checkResultByFile(@Nullable String message, @TestDataFile @NotNull String expectedFilePath, final boolean ignoreTrailingSpaces) {
    bringRealEditorBack();

    getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    if (ignoreTrailingSpaces) {
      final Editor editor = myEditor;
      TrailingSpacesStripper.strip(editor.getDocument(), false, true);
      EditorUtil.fillVirtualSpaceUntilCaret(editor);
    }

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    String fullPath = getTestDataPath() + expectedFilePath;

    File ioFile = new File(fullPath);

    assertTrue(getMessage("Cannot find file " + fullPath, message), ioFile.exists());
    String fileText;
    try {
     checkCaseSensitiveFS(fullPath, ioFile);
      fileText = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8_CHARSET);
    }
    catch (IOException e) {
      LOG.error(e);
      throw new RuntimeException(e);
    }
    checkResultByText(message, StringUtil.convertLineSeparators(fileText), ignoreTrailingSpaces, getTestDataPath() + "/" + expectedFilePath);
  }

  /**
   * Same as checkResultByFile but text is provided directly.
   */
  protected void checkResultByText(@NonNls @NotNull String expectedFileText) {
    checkResultByText(null, expectedFileText, false, null);
  }

  /**
   * Same as checkResultByFile but text is provided directly.
   * @param message - this check specific message. Added to text, caret position, selection checking. May be null
   * @param ignoreTrailingSpaces - whether trailing spaces in editor in data file should be stripped prior to comparing.
   */
  protected void checkResultByText(final String message, @NotNull String expectedFileText, final boolean ignoreTrailingSpaces) {
    checkResultByText(message, expectedFileText, ignoreTrailingSpaces, null);
  }

  /**
   * Same as checkResultByFile but text is provided directly.
   * @param message - this check specific message. Added to text, caret position, selection checking. May be null
   * @param ignoreTrailingSpaces - whether trailing spaces in editor in data file should be stripped prior to comparing.
   */
  protected void checkResultByText(final String message, @NotNull String expectedFileText, final boolean ignoreTrailingSpaces, final String filePath) {
    bringRealEditorBack();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    ApplicationManager.getApplication().runWriteAction(() -> {
      final Document document = EditorFactory.getInstance().createDocument(expectedFileText);

      if (ignoreTrailingSpaces) {
        ((DocumentImpl)document).stripTrailingSpaces(getProject());
      }

      EditorTestUtil.CaretAndSelectionState carets = EditorTestUtil.extractCaretAndSelectionMarkers(document);

      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      String newFileText = document.getText();

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      String fileText1 = myFile.getText();
      String failMessage = getMessage("Text mismatch", message);
      if (filePath != null && !newFileText.equals(fileText1)) {
        throw new FileComparisonFailure(failMessage, newFileText, fileText1, filePath);
      }
      assertEquals(failMessage, newFileText, fileText1);

      EditorTestUtil.verifyCaretAndSelectionState(myEditor, carets, message);
    });
  }

  protected static void checkResultByTextWithoutPSI(final String message,
                                                    @NotNull final Editor editor,
                                                    @NotNull final String fileText,
                                                    final boolean ignoreTrailingSpaces,
                                                    final String filePath) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final Document fakeDocument = EditorFactory.getInstance().createDocument(fileText);

      if (ignoreTrailingSpaces) {
        ((DocumentImpl)fakeDocument).stripTrailingSpaces(getProject());
      }

      EditorTestUtil.CaretAndSelectionState carets = EditorTestUtil.extractCaretAndSelectionMarkers(fakeDocument);

      String newFileText = fakeDocument.getText();
      String fileText1 = editor.getDocument().getText();
      String failMessage = getMessage("Text mismatch", message);
      if (filePath != null && !newFileText.equals(fileText1)) {
        throw new FileComparisonFailure(failMessage, newFileText, fileText1, filePath);
      }
      assertEquals(failMessage, newFileText, fileText1);

      EditorTestUtil.verifyCaretAndSelectionState(editor, carets, message);
    });
  }

  @NotNull
  private static String getMessage(@NonNls @NotNull String engineMessage, String userMessage) {
    if (userMessage == null) return engineMessage;
    return userMessage + " [" + engineMessage + "]";
  }

  /**
   * @return Editor used in test.
   */
  protected static Editor getEditor() {
    return myEditor;
  }

  /**
   * @return PsiFile opened in editor used in test
   */
  protected static PsiFile getFile() {
    return myFile;
  }

  protected static VirtualFile getVFile() {
    return myVFile;
  }

  protected static void bringRealEditorBack() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    if (myEditor instanceof EditorWindow) {
      Document document = ((DocumentWindow)myEditor.getDocument()).getDelegate();
      myFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
      myEditor = ((EditorWindow)myEditor).getDelegate();
      myVFile = myFile.getVirtualFile();
    }
  }

  protected void caretRight() {
    caretRight(getEditor());
  }
  public static void caretRight(@NotNull Editor editor) {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, editor);
  }

  protected void caretUp() {
    caretUp(getEditor());
  }

  public static void caretUp(@NotNull Editor editor) {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, editor);
  }

  protected void deleteLine() {
    deleteLine(getEditor(),getProject());
  }
  public static void deleteLine(@NotNull Editor editor, Project project) {
    executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE, editor,project);
  }

  protected void type(@NonNls @NotNull String s) {
    for (char c : s.toCharArray()) {
      type(c);
    }
  }
  protected void type(char c) {
    type(c, getEditor(),getProject());
  }

  public static void type(char c, @NotNull Editor editor, Project project) {
    if (c == '\n') {
      executeAction(IdeActions.ACTION_EDITOR_ENTER, editor,project);
    }
    else {
      EditorActionManager actionManager = EditorActionManager.getInstance();
      final DataContext dataContext = DataManager.getInstance().getDataContext();
      TypedAction action = actionManager.getTypedAction();
      action.actionPerformed(editor, c, dataContext);
    }
  }

  protected void backspace() {
    backspace(getEditor(),getProject());
  }

  public static void backspace(@NotNull final Editor editor, Project project) {
    executeAction(IdeActions.ACTION_EDITOR_BACKSPACE, editor,project);
  }

  protected void ctrlShiftF7() {
    HighlightUsagesHandler.invoke(getProject(), getEditor(), getFile());
  }

  protected void ctrlW() {
    ctrlW(getEditor(),getProject());
  }

  public static void ctrlW(@NotNull Editor editor, Project project) {
    executeAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET, editor,project);
  }

  public void ctrlD() {
    ctrlD(getEditor(),getProject());
  }
  public static void ctrlD(@NotNull Editor editor, Project project) {
    executeAction(IdeActions.ACTION_EDITOR_DUPLICATE, editor, project);
  }

  protected void delete() {
    delete(getEditor(), getProject());
  }
  public static void delete(@NotNull final Editor editor, Project project) {
    executeAction(IdeActions.ACTION_EDITOR_DELETE, editor, project);
  }

  protected static void home() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  protected static void end() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  protected static void homeWithSelection() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION);
  }

  protected static void endWithSelection() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION);
  }

  protected static void copy() {
    executeAction(IdeActions.ACTION_EDITOR_COPY);
  }

  protected static void paste() {
    executeAction(IdeActions.ACTION_EDITOR_PASTE);
  }

  protected static void moveCaretToPreviousWordWithSelection() {
    executeAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION);
  }

  protected static void moveCaretToNextWordWithSelection() {
    executeAction(IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION);
  }

  protected static void previousWord() {
    executeAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
  }

  protected static void nextWord() {
    executeAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
  }

  protected static void cutLineBackward() {
    executeAction("EditorCutLineBackward");
  }

  protected static void cutToLineEnd() {
    executeAction("EditorCutLineEnd");
  }
  
  protected static void deleteToLineStart() {
    executeAction("EditorDeleteToLineStart");
  }
  
  protected static void deleteToLineEnd() {
    executeAction("EditorDeleteToLineEnd");
  }
  
  protected static void killToWordStart() {
    executeAction("EditorKillToWordStart");
  }

  protected static void killToWordEnd() {
    executeAction("EditorKillToWordEnd");
  }

  protected static void killRegion() {
    executeAction("EditorKillRegion");
  }

  protected static void killRingSave() {
    executeAction("EditorKillRingSave");
  }

  protected static void unindent() {
    executeAction("EditorUnindentSelection");
  }

  protected static void selectLine() {
    executeAction("EditorSelectLine");
  }

  protected static void left() {
    executeAction("EditorLeft");
  }

  protected static void right() {
    executeAction("EditorRight");
  }

  protected static void leftWithSelection() {
    executeAction("EditorLeftWithSelection");
  }

  protected static void rightWithSelection() {
    executeAction("EditorRightWithSelection");
  }

  protected static void up() {
    executeAction("EditorUp");
  }

  protected static void down() {
    executeAction("EditorDown");
  }

  protected static void lineComment() {
    executeAction(IdeActions.ACTION_COMMENT_LINE);
  }

  protected static void executeAction(@NonNls @NotNull final String actionId) {
    executeAction(actionId, getEditor());
  }
  protected static void executeAction(@NonNls @NotNull final String actionId, @NotNull final Editor editor) {
    executeAction(actionId, editor, getProject());
  }
  public static void executeAction(@NonNls @NotNull final String actionId, @NotNull final Editor editor, Project project) {
    CommandProcessor.getInstance().executeCommand(project, () -> EditorTestUtil.executeAction(editor, actionId), "", null, editor.getDocument());
  }

  @NotNull
  protected static DataContext getCurrentEditorDataContext() {
    final DataContext defaultContext = DataManager.getInstance().getDataContext();
    return dataId -> {
      if (CommonDataKeys.EDITOR.is(dataId)) {
        return getEditor();
      }
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return getProject();
      }
      if (CommonDataKeys.PSI_FILE.is(dataId)) {
        return getFile();
      }
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        PsiFile file = getFile();
        if (file == null) return null;
        Editor editor = getEditor();
        if (editor == null) return null;
        return file.findElementAt(editor.getCaretModel().getOffset());
      }
      return defaultContext.getData(dataId);
    };
  }

  /**
   * file parameterized tests support
   * @see FileBasedTestCaseHelperEx
   * @Parameterized.Parameter fields are injected on parameterized test creation.
   */
  @Parameterized.Parameter(0)
  public String myFileSuffix;

  /**
   * path to the root of test data in case of com.intellij.testFramework.FileBasedTestCaseHelperEx
   * or
   * path to the directory with current test data in case of @TestDataPath
   */
  @Parameterized.Parameter(1)
  public String myTestDataPath;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> params() throws Throwable {
    return Collections.emptyList();
  }

  @com.intellij.testFramework.Parameterized.Parameters(name = "{0}")
  public static List<Object[]> params(@NotNull Class<?> klass) throws Throwable{
    final LightPlatformCodeInsightTestCase testCase = (LightPlatformCodeInsightTestCase)klass.newInstance();
    if (!(testCase instanceof FileBasedTestCaseHelper)) {
      fail("Parameterized test should implement FileBasedTestCaseHelper");
    }

    try {
      PathManagerEx.replaceLookupStrategy(klass, com.intellij.testFramework.Parameterized.class);
    }
    catch (IllegalArgumentException ignore) {
      //allow to run out of idea project
    }

    final FileBasedTestCaseHelper fileBasedTestCase = (FileBasedTestCaseHelper)testCase;
    String testDataPath = testCase.getTestDataPath();

    File testDir = null;
    if (fileBasedTestCase instanceof FileBasedTestCaseHelperEx) {
      testDir = new File(testDataPath, ((FileBasedTestCaseHelperEx)fileBasedTestCase).getRelativeBasePath());
    } else {
      final TestDataPath annotation = klass.getAnnotation(TestDataPath.class);
      if (annotation == null) {
        fail("TestCase should implement com.intellij.testFramework.FileBasedTestCaseHelperEx or be annotated with com.intellij.testFramework.TestDataPath");
      } else {
        final String trimmedRoot = StringUtil.trimStart(StringUtil.trimStart(annotation.value(), "$CONTENT_ROOT"), "$PROJECT_ROOT");
        final String lastPathComponent = new File(testDataPath).getName();
        final int idx = trimmedRoot.indexOf(lastPathComponent);
        testDataPath = testDataPath.replace(File.separatorChar, '/') + (idx > 0 ? trimmedRoot.substring(idx + lastPathComponent.length()) : trimmedRoot);
        testDir = new File(testDataPath);
      }
    }

    final File[] files = testDir.listFiles();

    if (files == null) {
      fail("Test files not found in " + testDir.getPath());
    }

    final List<Object[]> result = new ArrayList<>();
    for (File file : files) {
      final String fileSuffix = fileBasedTestCase.getFileSuffix(file.getName());
      if (fileSuffix != null) {
        result.add(new Object[] {fileSuffix, testDataPath});
      }
    }
    return result;
  }

  @Override
  public String getName() {
    if (myFileSuffix != null) {
      return "test" + myFileSuffix;
    }
    return super.getName();
  }

  @Before
  public void before() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    invokeTestRunnable(() -> {
      try {
        setUp();
      }
      catch (Throwable e) {
        throwables[0] = e;
      }
    });

    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

  @After
  public void after() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    invokeTestRunnable(() -> {
      try {
        tearDown();
      }
      catch (Throwable e) {
        throwables[0] = e;
      }
    });
    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

  protected void runSingleTest(@NotNull final Runnable testRunnable) throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    invokeTestRunnable(() -> {
      try {
        testRunnable.run();
      }
      catch (Throwable e) {
        throwables[0] = e;
      }
    });

    if (throwables[0] != null) {
      throw throwables[0];
    }
  }
}
