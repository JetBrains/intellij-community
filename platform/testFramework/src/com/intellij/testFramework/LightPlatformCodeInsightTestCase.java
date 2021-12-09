// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class LightPlatformCodeInsightTestCase extends LightPlatformTestCase implements TestIndexingModeSupporter {
  private Editor myEditor;
  private PsiFile myFile;
  private VirtualFile myVFile;
  private TestIndexingModeSupporter.IndexingMode myIndexingMode = IndexingMode.SMART;

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    boolean runInWriteAction = false;

    if (isRunInCommand()) {
      Ref<Throwable> e = new Ref<>();
      CommandProcessor.getInstance().executeCommand(getProject(), () -> {
        try {
          super.runTestRunnable(testRunnable);
        }
        catch (Throwable throwable) {
          e.set(throwable);
        }
      }, null, null);
      if (!e.isNull()) {
        throw e.get();
      }
    }
    else {
      super.runTestRunnable(testRunnable);
    }
  }

  protected boolean isRunInCommand() {
    return true;
  }

  /**
   * Configure test from data file. Data file is usual java, xml or whatever file that needs to be tested except it
   * has &lt;caret&gt; marker where caret should be placed when file is loaded in editor and &lt;selection&gt;&lt;/selection&gt;
   * denoting selection bounds.
   *
   * @param relativePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   */
  protected void configureByFile(@TestDataFile @NonNls @NotNull String relativePath) {
    try {
      String fullPath = getTestDataPath() + relativePath;
      final File ioFile = new File(fullPath);
      checkCaseSensitiveFS(fullPath, ioFile);
      String fileText = FileUtilRt.loadFile(ioFile, CharsetToolkit.UTF8, true);
      configureFromFileText(ioFile.getName(), fileText);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  protected String getAnswerFilePath() {
    return getTestDataPath() + myFileSuffix + ".txt";
  }

  private static void checkCaseSensitiveFS(@NotNull String fullOrRelativePath, @NotNull File ioFile) throws IOException {
    fullOrRelativePath = FileUtil.toSystemDependentName(FileUtil.toCanonicalPath(fullOrRelativePath));
    if (!ioFile.getCanonicalPath().endsWith(fullOrRelativePath)) {
      throw new RuntimeException("Queried for: " + fullOrRelativePath + "; but found: " + ioFile.getCanonicalPath());
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
  protected Document configureFromFileText(@NonNls @NotNull final String fileName, @NonNls @NotNull final String fileText) {
    return configureFromFileText(fileName, fileText, false);
  }

  /**
   * Same as configureByFile but text is provided directly.
   * @param fileName - name of the file.
   * @param fileText - data file text.
   * @param checkCaret - if true, if will be verified that file contains at least one caret or selection marker
   */
  @NotNull
  protected Document configureFromFileText(@NonNls @NotNull final String fileName,
                                           @NonNls @NotNull final String fileText,
                                           boolean checkCaret) {
    return WriteCommandAction.writeCommandAction(null).compute(() -> {
      final Document fakeDocument = new DocumentImpl(fileText);

      EditorTestUtil.CaretAndSelectionState caretsState = EditorTestUtil.extractCaretAndSelectionMarkers(fakeDocument);
      if (checkCaret) {
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
      EditorTestUtil.setCaretsAndSelection(getEditor(), caretsState);
      setupEditorForInjectedLanguage();
      getIndexingMode().ensureIndexingStatus(getProject());
      return document;
    });
  }

  @NotNull
  protected Editor configureFromFileTextWithoutPSI(@NonNls @NotNull final String fileText) {
    return WriteCommandAction.writeCommandAction(getProject()).compute(() -> {
      final Document fakeDocument = EditorFactory.getInstance().createDocument(fileText);
      EditorTestUtil.CaretAndSelectionState caretsState = EditorTestUtil.extractCaretAndSelectionMarkers(fakeDocument);

      String newFileText = fakeDocument.getText();
      Document document = EditorFactory.getInstance().createDocument(newFileText);
      final Editor editor = EditorFactory.getInstance().createEditor(document, getProject());
      ((EditorImpl)editor).setCaretActive();

      EditorTestUtil.setCaretsAndSelection(editor, caretsState);
      getIndexingMode().ensureIndexingStatus(getProject());
      return editor;
    });
  }

  @NotNull
  protected Editor createEditor(@NotNull VirtualFile file) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    Editor editor = FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file, 0), false);
    DaemonCodeAnalyzer.getInstance(getProject()).restart();
    assertNotNull(editor);
    ((EditorImpl)editor).setCaretActive();
    getIndexingMode().ensureIndexingStatus(getProject());
    return editor;
  }

  @NotNull
  private Document setupFileEditorAndDocument(@NotNull String relativePath, @NotNull String fileText) throws IOException {
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, StandardCharsets.UTF_8);
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    deleteVFile();

    myEditor = createSaveAndOpenFile(relativePath, fileText);
    myVFile = FileDocumentManager.getInstance().getFile(getEditor().getDocument());
    myFile = getPsiManager().findFile(myVFile);
    getIndexingMode().ensureIndexingStatus(getProject());
    return getEditor().getDocument();
  }

  @NotNull
  protected Editor createSaveAndOpenFile(@NotNull String relativePath, @NotNull String fileText) {
    Editor editor = createEditor(VfsTestUtil.createFile(getSourceRoot(), relativePath, fileText));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    getIndexingMode().ensureIndexingStatus(getProject());
    return editor;
  }

  @NotNull
  protected static VirtualFile createAndSaveFile(@NotNull String relativePath, @NotNull String fileText) {
    return VfsTestUtil.createFile(getSourceRoot(), relativePath, fileText);
  }

  protected void setupEditorForInjectedLanguage() {
    if (getEditor() != null) {
      Editor hostEditor = getEditor() instanceof EditorWindow ? ((EditorWindow)getEditor()).getDelegate() : getEditor();
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
        myVFile = myFile.getVirtualFile();
      }
    }
  }

  private void deleteVFile() throws IOException {
    if (myVFile != null) {
      if (myVFile instanceof VirtualFileWindow) myVFile = ((VirtualFileWindow)myVFile).getDelegate();
      WriteAction.run(() -> {
        // avoid messing with invalid files, in case someone calls configureXXX() several times
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        FileEditorManager.getInstance(getProject()).closeFile(myVFile);
        myVFile.delete(getProject());
      });
      getIndexingMode().ensureIndexingStatus(getProject());
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getIndexingMode().setUpTest(getProject(), getTestRootDisposable());
  }

  @Before  // runs after (all overrides of) setUp()
  public void before() throws Throwable {
    getIndexingMode().ensureIndexingStatus(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Project project = getProject();
      if (myIndexingMode != null && project != null) {
        myIndexingMode.tearDownTest(project);

        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        for (VirtualFile openFile : editorManager.getOpenFiles()) {
          editorManager.closeFile(openFile);
        }
      }
      deleteVFile();
      myEditor = null;
      myFile = null;
      myVFile = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
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

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    if (ignoreTrailingSpaces) {
      final Editor editor = getEditor();
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
      fileText = FileUtil.loadFile(ioFile, StandardCharsets.UTF_8);
    }
    catch (IOException e) {
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

      EditorTestUtil.verifyCaretAndSelectionState(getEditor(), carets, message);
    });
  }

  protected void checkResultByTextWithoutPSI(final String message,
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
  protected Editor getEditor() {
    return myEditor;
  }

  /**
   * @return PsiFile opened in editor used in test
   */
  protected PsiFile getFile() {
    return myFile;
  }

  protected VirtualFile getVFile() {
    return myVFile;
  }

  protected void bringRealEditorBack() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    if (getEditor() instanceof EditorWindow) {
      Document document = ((DocumentWindow)getEditor().getDocument()).getDelegate();
      myFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
      myEditor = ((EditorWindow)getEditor()).getDelegate();
      myVFile = myFile.getVirtualFile();
    }
  }

  protected void caretRight() {
    caretRight(getEditor());
  }
  public void caretRight(@NotNull Editor editor) {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, editor);
  }

  protected void caretUp() {
    caretUp(getEditor());
  }

  public void caretUp(@NotNull Editor editor) {
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
      DataContext dataContext = DataManager.getInstance().getDataContext();
      TypedAction action = TypedAction.getInstance();
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

  protected void home() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  protected void end() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  protected void homeWithSelection() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION);
  }

  protected void endWithSelection() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION);
  }

  protected void copy() {
    executeAction(IdeActions.ACTION_EDITOR_COPY);
  }

  protected void paste() {
    executeAction(IdeActions.ACTION_EDITOR_PASTE);
  }

  protected void moveCaretToPreviousWordWithSelection() {
    executeAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION);
  }

  protected void moveCaretToNextWordWithSelection() {
    executeAction(IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION);
  }

  protected void previousWord() {
    executeAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
  }

  protected void nextWord() {
    executeAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
  }

  protected void cutLineBackward() {
    executeAction("EditorCutLineBackward");
  }

  protected void cutToLineEnd() {
    executeAction("EditorCutLineEnd");
  }

  protected void deleteToLineStart() {
    executeAction("EditorDeleteToLineStart");
  }

  protected void deleteToLineEnd() {
    executeAction("EditorDeleteToLineEnd");
  }

  protected void killToWordStart() {
    executeAction("EditorKillToWordStart");
  }

  protected void killToWordEnd() {
    executeAction("EditorKillToWordEnd");
  }

  protected void killRegion() {
    executeAction("EditorKillRegion");
  }

  protected void killRingSave() {
    executeAction("EditorKillRingSave");
  }

  protected void unindent() {
    executeAction("EditorUnindentSelection");
  }

  protected void selectLine() {
    executeAction("EditorSelectLine");
  }

  protected void left() {
    executeAction("EditorLeft");
  }

  protected void right() {
    executeAction("EditorRight");
  }

  protected void leftWithSelection() {
    executeAction("EditorLeftWithSelection");
  }

  protected void rightWithSelection() {
    executeAction("EditorRightWithSelection");
  }

  protected void up() {
    executeAction("EditorUp");
  }

  protected void down() {
    executeAction("EditorDown");
  }

  protected void lineComment() {
    executeAction(IdeActions.ACTION_COMMENT_LINE);
  }

  protected void executeAction(@NonNls @NotNull final String actionId) {
    executeAction(actionId, getEditor());
  }
  protected void executeAction(@NonNls @NotNull final String actionId, @NotNull final Editor editor) {
    executeAction(actionId, editor, getProject());
  }
  public static void executeAction(@NonNls @NotNull final String actionId, @NotNull final Editor editor, Project project) {
    CommandProcessor.getInstance().executeCommand(project, () -> EditorTestUtil.executeAction(editor, actionId, true), "", null, editor.getDocument());
  }

  @NotNull
  protected DataContext getCurrentEditorDataContext() {
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
  @Parameterized.Parameter
  public String myFileSuffix;

  /**
   * path to the root of test data in case of com.intellij.testFramework.FileBasedTestCaseHelperEx
   * or
   * path to the directory with current test data in case of @TestDataPath
   */
  @Parameterized.Parameter(1)
  public String myTestDataPath;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> params() {
    return Collections.emptyList();
  }

  @com.intellij.testFramework.Parameterized.Parameters(name = "{0}")
  public static List<Object[]> params(@NotNull Class<?> klass) throws Throwable{
    final Object testCase = klass.newInstance();
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

    String testDataPath;
    if (testCase instanceof LightPlatformCodeInsightTestCase) {
      testDataPath = ((LightPlatformCodeInsightTestCase)testCase).getTestDataPath();
    }
    else {
      try {
        Method dataPath = klass.getDeclaredMethod("getTestDataPath");
        dataPath.setAccessible(true);
        testDataPath = (String)dataPath.invoke(fileBasedTestCase);
      }
      catch (Throwable e) {
        testDataPath = PathManagerEx.getTestDataPath();
      }
    }

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

    final Set<String> beforeFileSuffixes = new HashSet<>();
    final Set<String> afterFileSuffixes = new HashSet<>();
    final List<Object[]> result = new ArrayList<>();
    for (File file : files) {
      final String fileSuffix = fileBasedTestCase.getFileSuffix(file.getName());
      String fileAfterSuffix = fileBasedTestCase.getBaseName(file.getName());
      if (fileAfterSuffix != null) {
        afterFileSuffixes.add(fileAfterSuffix);
      }
      if (fileSuffix != null) {
        beforeFileSuffixes.add(fileSuffix);
        result.add(new Object[] {fileSuffix, testDataPath});
      }
    }
    afterFileSuffixes.removeAll(beforeFileSuffixes);
    if (!afterFileSuffixes.isEmpty()) {
      fail("'After' file has no corresponding 'before' file: " + String.join(", ", afterFileSuffixes));
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

  protected void setEditor(Editor editor) {
    myEditor = editor;
  }

  protected void setFile(PsiFile file) {
    myFile = file;
  }

  protected void setVFile(VirtualFile virtualFile) {
    myVFile = virtualFile;
  }

  @Override
  public void setIndexingMode(@NotNull IndexingMode mode) {
    myIndexingMode = mode;
  }

  @Override
  public @NotNull IndexingMode getIndexingMode() {
    return myIndexingMode;
  }
}
