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
package com.intellij.testFramework;

import com.intellij.codeInsight.generation.CommentByLineCommentHandler;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.TrailingSpacesStripper;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Segment;
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
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.LightCodeInsightTestCase");

  protected static Editor myEditor;
  protected static PsiFile myFile;
  protected static VirtualFile myVFile;

  @Override
  protected void runTest() throws Throwable {
    final Throwable[] throwable = {null};
    Runnable action = new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          @Override
          public void run() {

            try {
              doRunTest();
            }
            catch (Throwable t) {
              throwable[0] = t;
            }
          }
        }, "", null);
      }
    };
    if (isRunInWriteAction()) {
      ApplicationManager.getApplication().runWriteAction(action);
    }
    else {
      action.run();
    }

    if (throwable[0] != null) {
      throw throwable[0];
    }
  }

  protected void doRunTest() throws Throwable {
    LightPlatformCodeInsightTestCase.super.runTest();
  }

  protected boolean isRunInWriteAction() {
    return true;
  }

  /**
   * Configure test from data file. Data file is usual java, xml or whatever file that needs to be tested except it
   * has &lt;caret&gt; marker where caret should be placed when file is loaded in editor and &lt;selection&gt;&lt;/selection&gt;
   * denoting selection bounds.
   * @param filePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   */
  protected void configureByFile(@TestDataFile @NonNls @NotNull String filePath) {
    try {
      final File ioFile = new File(getTestDataPath() + filePath);
      String fileText = FileUtilRt.loadFile(ioFile, CharsetToolkit.UTF8, true);
      configureFromFileText(ioFile.getName(), fileText);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
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

  protected VirtualFile getVirtualFile(@NonNls String filePath) {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);
    return vFile;
  }

  /**
   * Same as configureByFile but text is provided directly.
   * @param fileName - name of the file.
   * @param fileText - data file text.
   * @throws java.io.IOException
   */
  @NotNull
  protected static Document configureFromFileText(@NonNls @NotNull final String fileName, @NonNls @NotNull final String fileText) throws IOException {
    return new WriteCommandAction<Document>(null) {
      @Override
      protected void run(@NotNull Result<Document> result) throws Throwable {
        if (myVFile != null) {
          // avoid messing with invalid files, in case someone calls configureXXX() several times
          PsiDocumentManager.getInstance(ourProject).commitAllDocuments();
          FileEditorManager.getInstance(ourProject).closeFile(myVFile);
          try {
            myVFile.delete(this);
          }
          catch (IOException e) {
            LOG.error(e);
          }
          myVFile = null;
        }
        final Document fakeDocument = new DocumentImpl(fileText);

        EditorTestUtil.CaretsState caretsState = EditorTestUtil.extractCaretAndSelectionMarkers(fakeDocument);

        String newFileText = fakeDocument.getText();
        Document document;
        try {
          document = setupFileEditorAndDocument(fileName, newFileText);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        setupCaretAndSelection(caretsState, newFileText);
        setupEditorForInjectedLanguage();
        result.setResult(document);
      }
    }.execute().getResultObject();
  }

  private static void setupCaretAndSelection(EditorTestUtil.CaretsState caretsState, String fileText) {
    List<EditorTestUtil.Caret> carets = caretsState.carets;
    if (myEditor.getCaretModel().supportsMultipleCarets()) {
      List<LogicalPosition> caretPositions = new ArrayList<LogicalPosition>();
      List<Segment> selections = new ArrayList<Segment>();
      for (EditorTestUtil.Caret caret : carets) {
        LogicalPosition pos = null;
        if (caret.offset != null) {
          int caretLine = StringUtil.offsetToLineNumber(fileText, caret.offset);
          int caretCol = EditorUtil.calcColumnNumber(null, myEditor.getDocument().getText(),
                                                     myEditor.getDocument().getLineStartOffset(caretLine), caret.offset,
                                                     CodeStyleSettingsManager.getSettings(getProject()).getIndentOptions(StdFileTypes.JAVA).TAB_SIZE);
          pos = new LogicalPosition(caretLine, caretCol);
        }
        caretPositions.add(pos);
        selections.add(caret.selection == null ? null : caret.selection);
      }
      myEditor.getCaretModel().setCarets(caretPositions, selections);
    }
    else {
      assertEquals("Caret model doesn't support multiple carets", 1, carets.size());
      EditorTestUtil.Caret caret = carets.get(0);
      if (caret.offset != null) {
        int caretLine = StringUtil.offsetToLineNumber(fileText, caret.offset);
        int caretCol = EditorUtil.calcColumnNumber(null, myEditor.getDocument().getText(),
                                                   myEditor.getDocument().getLineStartOffset(caretLine), caret.offset,
                                                   CodeStyleSettingsManager.getSettings(getProject()).getIndentOptions(StdFileTypes.JAVA).TAB_SIZE);
        LogicalPosition pos = new LogicalPosition(caretLine, caretCol);
        myEditor.getCaretModel().moveToLogicalPosition(pos);
      }
      if (caret.selection != null) {
        myEditor.getSelectionModel().setSelection(caret.selection.getStartOffset(), caret.selection.getEndOffset());
      }
    }
  }

  protected static Editor createEditor(@NotNull VirtualFile file) {
    Editor editor = FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file, 0), false);
    ((EditorImpl)editor).setCaretActive();
    return editor;
  }

  @NotNull
  private static Document setupFileEditorAndDocument(@NotNull String fileName, @NotNull String fileText) throws IOException {
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, CharsetToolkit.UTF8_CHARSET);
    EncodingProjectManager.getInstance(ProjectManager.getInstance().getDefaultProject()).setEncoding(null, CharsetToolkit.UTF8_CHARSET);
    PostprocessReformattingAspect.getInstance(ourProject).doPostponedFormatting();
    deleteVFile();
    myVFile = getSourceRoot().createChildData(null, fileName);
    VfsUtil.saveText(myVFile, fileText);
    final FileDocumentManager manager = FileDocumentManager.getInstance();
    final Document document = manager.getDocument(myVFile);
    assertNotNull("Can't create document for '" + fileName + "'", document);
    manager.reloadFromDisk(document);
    document.insertString(0, " ");
    document.deleteString(0, 1);
    myFile = getPsiManager().findFile(myVFile);
    assertNotNull("Can't create PsiFile for '" + fileName + "'. Unknown file type most probably.", myFile);
    assertTrue(myFile.isPhysical());
    myEditor = createEditor(myVFile);
    myVFile.setCharset(CharsetToolkit.UTF8_CHARSET);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    return document;
  }

  private static void setupEditorForInjectedLanguage() {
    Editor editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myFile);
    if (editor instanceof EditorWindow) {
      myFile = ((EditorWindow)editor).getInjectedFile();
      myEditor = editor;
    }
  }

  private static void deleteVFile() {
    if (myVFile != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            myVFile.delete(this);
          } catch (IOException e) {
            LOG.error(e);
          }
        }
      });
    }
  }

  @Override
  protected void tearDown() throws Exception {
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      editorManager.closeFile(openFile);
    }
    deleteVFile();
    myEditor = null;
    myFile = null;
    myVFile = null;
    super.tearDown();
  }

  /**
   * Validates that content of the editor as well as caret and selection matches one specified in data file that
   * should be formed with the same format as one used in configureByFile
   * @param filePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   */
  protected void checkResultByFile(@TestDataFile @NonNls @NotNull String filePath) {
    checkResultByFile(null, filePath, false);
  }

  /**
   * Validates that content of the editor as well as caret and selection matches one specified in data file that
   * should be formed with the same format as one used in configureByFile
   * @param message - this check specific message. Added to text, caret position, selection checking. May be null
   * @param filePath - relative path from %IDEA_INSTALLATION_HOME%/testData/
   * @param ignoreTrailingSpaces - whether trailing spaces in editor in data file should be stripped prior to comparing.
   */
  protected void checkResultByFile(@Nullable String message, @TestDataFile @NotNull String filePath, final boolean ignoreTrailingSpaces) {
    bringRealEditorBack();

    getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    if (ignoreTrailingSpaces) {
      final Editor editor = myEditor;
      TrailingSpacesStripper.stripIfNotCurrentLine(editor.getDocument(), false);
      EditorUtil.fillVirtualSpaceUntilCaret(editor);
    }

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    String fullPath = getTestDataPath() + filePath;

    File ioFile = new File(fullPath);

    assertTrue(getMessage("Cannot find file " + fullPath, message), ioFile.exists());
    String fileText = null;
    try {
      fileText = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8);
    } catch (IOException e) {
      LOG.error(e);
    }
    checkResultByText(message, StringUtil.convertLineSeparators(fileText), ignoreTrailingSpaces, getTestDataPath() + "/" + filePath);
  }

  /**
   * Same as checkResultByFile but text is provided directly.
   */
  protected void checkResultByText(@NonNls @NotNull String fileText) {
    checkResultByText(null, fileText, false, null);
  }

  /**
     * Same as checkResultByFile but text is provided directly.
     * @param message - this check specific message. Added to text, caret position, selection checking. May be null
     * @param ignoreTrailingSpaces - whether trailing spaces in editor in data file should be stripped prior to comparing.
     */
  protected void checkResultByText(final String message, @NotNull String fileText, final boolean ignoreTrailingSpaces) {
    checkResultByText(message, fileText, ignoreTrailingSpaces, null);  
  }
  
  /**
   * Same as checkResultByFile but text is provided directly.
   * @param message - this check specific message. Added to text, caret position, selection checking. May be null
   * @param ignoreTrailingSpaces - whether trailing spaces in editor in data file should be stripped prior to comparing.
   */
  protected void checkResultByText(final String message, @NotNull final String fileText, final boolean ignoreTrailingSpaces, final String filePath) {
    bringRealEditorBack();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final Document document = EditorFactory.getInstance().createDocument(fileText);

        if (ignoreTrailingSpaces) {
          ((DocumentImpl)document).stripTrailingSpaces(getProject());
        }

        EditorTestUtil.CaretsState carets = EditorTestUtil.extractCaretAndSelectionMarkers(document);

        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
        String newFileText = document.getText();

        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        String fileText = myFile.getText();
        String failMessage = getMessage("Text mismatch", message);
        if (filePath != null && !newFileText.equals(fileText)) {
          throw new FileComparisonFailure(failMessage, newFileText, fileText, filePath);
        }
        assertEquals(failMessage, newFileText, fileText);

        checkCaretAndSelectionPositions(carets, newFileText, message);
      }
    });
  }

  private static String getMessage(@NonNls String engineMessage, String userMessage) {
    if (userMessage == null) return engineMessage;
    return userMessage + " [" + engineMessage + "]";
  }

  private static String getCaretDescription(int caretNumber, int totalCarets) {
    return totalCarets == 1 ? "" : "(caret " + (caretNumber + 1) + "/" + totalCarets + ")";
  }

  @SuppressWarnings("ConstantConditions")
  private static void checkCaretAndSelectionPositions(EditorTestUtil.CaretsState caretState, String newFileText, String message) {
    CaretModel caretModel = myEditor.getCaretModel();
    List<Caret> allCarets = new ArrayList<Caret>(caretModel.getAllCarets());
    assertEquals("Unexpected number of carets", caretState.carets.size(), allCarets.size());
    for (int i = 0; i < caretState.carets.size(); i++) {
      String caretDescription = getCaretDescription(i, caretState.carets.size());
      Caret currentCaret = allCarets.get(i);
      LogicalPosition actualCaretPosition = currentCaret.getLogicalPosition();
      EditorTestUtil.Caret expected = caretState.carets.get(i);
      if (expected.offset != null) {
        int caretLine = StringUtil.offsetToLineNumber(newFileText, expected.offset);
        int caretCol = EditorUtil.calcColumnNumber(null, newFileText,
                                                   StringUtil.lineColToOffset(newFileText, caretLine, 0),
                                                   expected.offset,
                                                   CodeStyleSettingsManager.getSettings(getProject()).getIndentOptions(StdFileTypes.JAVA).TAB_SIZE);

        assertEquals(getMessage("caretLine" + caretDescription, message), caretLine + 1, actualCaretPosition.line + 1);
        assertEquals(getMessage("caretColumn" + caretDescription, message), caretCol + 1, actualCaretPosition.column + 1);
      }
      if (expected.selection != null) {
        int selStartLine = StringUtil.offsetToLineNumber(newFileText, expected.selection.getStartOffset());
        int selStartCol = expected.selection.getStartOffset() - StringUtil.lineColToOffset(newFileText, selStartLine, 0);

        int selEndLine = StringUtil.offsetToLineNumber(newFileText, expected.selection.getEndOffset());
        int selEndCol = expected.selection.getEndOffset() - StringUtil.lineColToOffset(newFileText, selEndLine, 0);

        assertEquals(
            getMessage("selectionStartLine" + caretDescription, message),
            selStartLine + 1,
            StringUtil.offsetToLineNumber(newFileText, currentCaret.getSelectionStart()) + 1);

        assertEquals(
            getMessage("selectionStartCol" + caretDescription, message),
            selStartCol + 1,
            currentCaret.getSelectionStart() - StringUtil.lineColToOffset(newFileText, selStartLine, 0) + 1);

        assertEquals(
          getMessage("selectionEndLine" + caretDescription, message),
            selEndLine + 1,
            StringUtil.offsetToLineNumber(newFileText, currentCaret.getSelectionEnd()) + 1);

        assertEquals(
            getMessage("selectionEndCol" + caretDescription, message),
            selEndCol + 1,
            currentCaret.getSelectionEnd() - StringUtil.lineColToOffset(newFileText, selEndLine, 0) + 1);
      }
      else {
        assertFalse(getMessage("must not have selection" + caretDescription, message), currentCaret.hasSelection());
      }
    }
  }

  @Override
  public Object getData(String dataId) {
    if (CommonDataKeys.EDITOR.is(dataId)) {
      return myEditor;
    }
    if (dataId.equals(AnActionEvent.injectedId(CommonDataKeys.EDITOR.getName()))) {
      return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(getEditor(), getFile());
    }
    if (CommonDataKeys.PSI_FILE.is(dataId)) {
      return myFile;
    }
    if (dataId.equals(AnActionEvent.injectedId(CommonDataKeys.PSI_FILE.getName()))) {
      Editor editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(getEditor(), getFile());
      return editor instanceof EditorWindow ? ((EditorWindow)editor).getInjectedFile() : getFile();
    }
    return super.getData(dataId);
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

  protected void caretUp() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler action = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
    action.execute(getEditor(), DataManager.getInstance().getDataContext());
  }
  protected void deleteLine() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler action = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_DELETE_LINE);
    action.execute(getEditor(), DataManager.getInstance().getDataContext());
  }
  protected static void type(char c) {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    final DataContext dataContext = DataManager.getInstance().getDataContext();
    if (c == '\n') {
      actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER).execute(getEditor(), dataContext);
    }
    else if (c == '\b') {
      actionManager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE).execute(getEditor(), dataContext);
    }
    else {
      actionManager.getTypedAction().actionPerformed(getEditor(), c, dataContext);
    }
  }

  protected static void type(@NonNls String s) {
    for (char c : s.toCharArray()) {
      type(c);
    }
  }
  protected static void backspace() {
    executeAction(IdeActions.ACTION_EDITOR_BACKSPACE);
  }
  protected static void delete() {
    executeAction(IdeActions.ACTION_EDITOR_DELETE);
  }

  protected static void home() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  protected static void end() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  protected static void copy() {
    executeAction(IdeActions.ACTION_EDITOR_COPY);
  }

  protected static void paste() {
    executeAction(IdeActions.ACTION_EDITOR_PASTE);
  }
  
  protected static void moveCaretToPreviousWordWithSelection() {
    executeAction("EditorPreviousWordWithSelection");
  }

  protected static void moveCaretToNextWordWithSelection() {
    executeAction("EditorNextWordWithSelection");
  }

  protected static void cutLineBackward() {
    executeAction("EditorCutLineBackward");
  }
  
  protected static void cutToLineEnd() {
    executeAction("EditorCutLineEnd");
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

  protected static void lineComment() {
    new CommentByLineCommentHandler().invoke(getProject(), getEditor(), getFile());
  }
  
  protected static void executeAction(@NonNls @NotNull final String actionId) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        EditorActionManager actionManager = EditorActionManager.getInstance();
        EditorActionHandler actionHandler = actionManager.getActionHandler(actionId);
        actionHandler.executeInCaretContext(getEditor(), null, DataManager.getInstance().getDataContext());
      }
    }, "", null);
  }

  protected static DataContext getCurrentEditorDataContext() {
    final DataContext defaultContext = DataManager.getInstance().getDataContext();
    return new DataContext() {
      @Override
      @Nullable
      public Object getData(@NonNls String dataId) {
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
      }
    };
  }

  /**
   * file parameterized tests support
   * @see FileBasedTestCaseHelperEx
   */

  /**
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
  public static List<Object[]> params(Class<?> klass) throws Throwable{
    final LightPlatformCodeInsightTestCase testCase = (LightPlatformCodeInsightTestCase)klass.newInstance();
    if (!(testCase instanceof FileBasedTestCaseHelper)) {
      fail("Parameterized test should implement FileBasedTestCaseHelper");
    }

    PathManagerEx.replaceLookupStrategy(klass, com.intellij.testFramework.Parameterized.class);

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

    final List<Object[]> result = new ArrayList<Object[]>();
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

    invokeTestRunnable(new Runnable() {
      @Override
      public void run() {
        try {
          setUp();
        }
        catch (Throwable e) {
          throwables[0] = e;
        }
      }
    });

    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

  @After
  public void after() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    invokeTestRunnable(new Runnable() {
      @Override
      public void run() {
        try {
          tearDown();
        }
        catch (Throwable e) {
          throwables[0] = e;
        }
      }
    });
    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

}
