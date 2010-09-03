/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.highlighting.actions.HighlightUsagesAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings({"TestMethodWithIncorrectSignature"})
public class CodeInsightTestFixtureImpl extends BaseFixture implements CodeInsightTestFixture {

  @NonNls private static final String PROFILE = "Configurable";

  private PsiManagerImpl myPsiManager;
  private PsiFile myFile;
  private Editor myEditor;
  private String myTestDataPath;
  private boolean myEmptyLookup;

  private InspectionProfileEntry[] myInspections;
  private final Map<String, InspectionProfileEntry> myAvailableTools = new THashMap<String, InspectionProfileEntry>();
  private final Map<String, InspectionTool> myAvailableLocalTools = new THashMap<String, InspectionTool>();

  private final TempDirTestFixture myTempDirFixture;
  protected final IdeaProjectTestFixture myProjectFixture;
  @NonNls private static final String XXX = "XXX";
  private PsiElement myFileContext;
  private final FileTreeAccessFilter myJavaFilesFilter = new FileTreeAccessFilter();
  private boolean myAllowDirt;

  public CodeInsightTestFixtureImpl(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirTestFixture) {
    myProjectFixture = projectFixture;
    myTempDirFixture = tempDirTestFixture;
  }

  public void setTestDataPath(String dataPath) {
    myTestDataPath = dataPath;
  }

  public String getTempDirPath() {
    return myTempDirFixture.getTempDirPath();
  }

  public TempDirTestFixture getTempDirFixture() {
    return myTempDirFixture;
  }

  public VirtualFile copyFileToProject(@NonNls final String sourceFilePath, @NonNls final String targetPath) {
    File fromFile = new File(getTestDataPath() + "/" + sourceFilePath);
    if (!fromFile.exists()) {
      fromFile = new File(sourceFilePath);
//      assert fromFile.exists(): "cannot find " + getTestDataPath() + "/" + sourceFilePath;
    }

    if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
      VirtualFile fromVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fromFile);
      if (fromVFile == null) {
        fromVFile = myTempDirFixture.getFile(sourceFilePath);
      }
      assert fromVFile != null: "can't find testdata file " + sourceFilePath;
      return myTempDirFixture.copyFile(fromVFile, targetPath);
    }
    final File destFile = new File(getTempDirPath() + "/" + targetPath);
    if (!destFile.exists()) {

      if (fromFile.isDirectory()) {
        destFile.mkdirs();
      }
      else {
        try {
          FileUtil.copy(fromFile, destFile);
        }
        catch (IOException e) {
          throw new RuntimeException("Cannot copy " + fromFile + " to " + destFile, e);
        }
      }
    }
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destFile);
    Assert.assertNotNull(file);
    return file;
  }

  public VirtualFile copyDirectoryToProject(@NonNls final String sourceFilePath, @NonNls final String targetPath)  {
    assert getTestDataPath() != null: "test data path not specified";
    final File fromFile = new File(getTestDataPath() + "/" + sourceFilePath);
    if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
      return myTempDirFixture.copyAll(fromFile.getPath(), targetPath);
    }
    else {
      final File destFile = new File(getTempDirPath() + "/" + targetPath);
      try {
        FileUtil.copyDir(fromFile, destFile);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destFile);
      Assert.assertNotNull(file);
      file.refresh(false, true);
      return file;
    }
  }

  public VirtualFile copyFileToProject(@NonNls final String sourceFilePath) {
    return copyFileToProject(sourceFilePath, sourceFilePath);
  }

  public void enableInspections(InspectionProfileEntry... inspections) {
    myInspections = inspections;
    if (isInitialized()) {
      configureInspections(myInspections);
    }
  }

  private boolean isInitialized() {
    return myPsiManager != null;
  }

  public void enableInspections(final Class<? extends LocalInspectionTool>... inspections) {
    final ArrayList<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>();
    for (Class clazz: inspections) {
      try {
        LocalInspectionTool inspection = (LocalInspectionTool)clazz.getConstructor().newInstance();
        tools.add(inspection);
      }
      catch (Exception e) {
        throw new RuntimeException("Cannot instantiate " + clazz);
      }
    }
    enableInspections(tools.toArray(new LocalInspectionTool[tools.size()]));
  }

  public void disableInspections(InspectionProfileEntry... inspections) {
    myAvailableTools.clear();
    myAvailableLocalTools.clear();
    final ArrayList<InspectionProfileEntry> tools = new ArrayList<InspectionProfileEntry>(Arrays.asList(myInspections));
    for (Iterator<InspectionProfileEntry> i = tools.iterator(); i.hasNext();) {
      final InspectionProfileEntry tool = i.next();
      for (InspectionProfileEntry toRemove: inspections) {
        if (tool.getShortName().equals(toRemove.getShortName())) {
          i.remove();
          break;
        }
      }
    }
    myInspections = tools.toArray(new InspectionProfileEntry[tools.size()]);
    configureInspections(myInspections);
  }

  public void enableInspections(InspectionToolProvider... providers) {
    final ArrayList<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>();
    for (InspectionToolProvider provider: providers) {
      for (Class clazz: provider.getInspectionClasses()) {
        try {
          Object o = clazz.getConstructor().newInstance();
          if (o instanceof LocalInspectionTool) {
            LocalInspectionTool inspection = (LocalInspectionTool)o;
            tools.add(inspection);
          }
        }
        catch (Exception e) {
          throw new RuntimeException("Cannot instantiate " + clazz, e);
        }
      }
    }
    myInspections = tools.toArray(new LocalInspectionTool[tools.size()]);
    configureInspections(myInspections);
  }

  public long testHighlighting(final boolean checkWarnings,
                               final boolean checkInfos,
                               final boolean checkWeakWarnings,
                               final String... filePaths) {
    final Ref<Long> duration = new Ref<Long>();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Exception {
        if (filePaths.length > 0) {
          configureByFilesInner(filePaths);
        }
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, duration);
      }
    }.execute().throwException();
    return duration.get().longValue();
  }

  public long testHighlightingAllFiles(final boolean checkWarnings,
                                       final boolean checkInfos,
                                       final boolean checkWeakWarnings,
                                       @NonNls final String... filePaths) {
    final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    for (String path : filePaths) {
      files.add(copyFileToProject(path));
    }
    return testHighlightingAllFiles(checkWarnings, checkInfos, checkWeakWarnings, VfsUtil.toVirtualFileArray(files));
  }

  public long testHighlightingAllFiles(final boolean checkWarnings,
                               final boolean checkInfos,
                               final boolean checkWeakWarnings,
                               @NonNls final VirtualFile... files) {
    final Ref<Long> duration = new Ref<Long>();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Exception {
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, duration, files);
      }
    }.execute().throwException();
    return duration.get().longValue();
  }

  private void collectAndCheckHighlightings(final boolean checkWarnings, final boolean checkInfos, final boolean checkWeakWarnings, final Ref<Long> duration,
                                            final VirtualFile[] files) {
    final List<Trinity<PsiFile, Editor, ExpectedHighlightingData>> datas = ContainerUtil.map2List(files, new Function<VirtualFile, Trinity<PsiFile, Editor, ExpectedHighlightingData>>() {
      public Trinity<PsiFile, Editor, ExpectedHighlightingData> fun(final VirtualFile file) {
        final PsiFile psiFile = myPsiManager.findFile(file);
        assertNotNull(psiFile);
        final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
        assertNotNull(document);
        return Trinity.create(psiFile, createEditor(file), new ExpectedHighlightingData(document, checkWarnings, checkWeakWarnings, checkInfos, psiFile));
      }
    });
    for (Trinity<PsiFile, Editor, ExpectedHighlightingData> trinity : datas) {
      myEditor = trinity.second;
      myFile = trinity.first;
      collectAndCheckHighlightings(trinity.third, duration);
    }
  }

  public long checkHighlighting(final boolean checkWarnings, final boolean checkInfos, final boolean checkWeakWarnings) {
    final Ref<Long> duration = new Ref<Long>();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {
      protected void run() throws Exception {
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, duration);
      }
    }.execute().throwException();
    return duration.get().longValue();
  }

  public long checkHighlighting() {
    return checkHighlighting(true, true, true);
  }

  public long testHighlighting(final String... filePaths) {
    return testHighlighting(true, true, true, filePaths);
  }

  public long testHighlighting(final boolean checkWarnings, final boolean checkInfos, final boolean checkWeakWarnings, final VirtualFile file) {
    final Ref<Long> duration = new Ref<Long>();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {
      protected void run() throws Exception {
        openFileInEditor(file);
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, duration);
      }
    }.execute().throwException();
    return duration.get().longValue();
  }

  public void openFileInEditor(@NotNull final VirtualFile file) {
    myFile = myPsiManager.findFile(file);
    myEditor = createEditor(file);
  }

  public void testInspection(String testDir, InspectionTool tool) {
    VirtualFile sourceDir = copyDirectoryToProject(new File(testDir, "src").getPath(), "src");
    AnalysisScope scope = new AnalysisScope(getPsiManager().findDirectory(sourceDir));

    InspectionManagerEx inspectionManager = (InspectionManagerEx) InspectionManager.getInstance(getProject());
    final GlobalInspectionContextImpl globalContext = inspectionManager.createNewGlobalContext(!(myProjectFixture instanceof LightIdeaTestFixture));
    globalContext.setCurrentScope(scope);
    scope.invalidate();

    InspectionTestUtil.runTool(tool, scope, globalContext, inspectionManager);
    InspectionTestUtil.compareToolResults(tool, false, new File(getTestDataPath(), testDir).getPath());
  }

  @Nullable
  public PsiReference getReferenceAtCaretPosition(final String... filePaths) {
    new WriteCommandAction<PsiReference>(myProjectFixture.getProject()) {
      protected void run(final Result<PsiReference> result) throws Exception {
        configureByFilesInner(filePaths);
      }
    }.execute().throwException();
    return getFile().findReferenceAt(myEditor.getCaretModel().getOffset());
  }

  @NotNull
  public PsiReference getReferenceAtCaretPositionWithAssertion(final String... filePaths) {
    final PsiReference reference = getReferenceAtCaretPosition(filePaths);
    assert reference != null: "no reference found at " + myEditor.getCaretModel().getLogicalPosition();
    return reference;
  }

  @NotNull
  public List<IntentionAction> getAvailableIntentions(final String... filePaths) {

    return new WriteCommandAction<List<IntentionAction>>(myProjectFixture.getProject()) {
      protected void run(final Result<List<IntentionAction>> result) throws Exception {
        if (filePaths.length > 0) {
          configureByFilesInner(filePaths);
        }
        result.setResult(getAvailableIntentions());
      }
    }.execute().throwException().getResultObject();
  }

  @NotNull
  public List<IntentionAction> getAllQuickFixes(@NonNls final String... filePaths) {
    return new WriteCommandAction<List<IntentionAction>>(myProjectFixture.getProject()) {
      protected void run(final Result<List<IntentionAction>> result) throws Exception {
        configureByFilesInner(filePaths);
        List<HighlightInfo> infos = doHighlighting();
        ArrayList<IntentionAction> actions = new ArrayList<IntentionAction>();
        for (HighlightInfo info : infos) {
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            actions.add(pair.getFirst().getAction());
          }
        }
        result.setResult(actions);
      }
    }.execute().throwException().getResultObject();
  }

  @NotNull
  public List<IntentionAction> getAvailableIntentions() {
    doHighlighting();
    return getAvailableIntentions(myEditor, myFile);
  }

  public List<IntentionAction> filterAvailableIntentions(@NotNull final String hint) {
    final List<IntentionAction> availableIntentions = getAvailableIntentions();
    return ContainerUtil.findAll(availableIntentions, new Condition<IntentionAction>() {
      public boolean value(final IntentionAction intentionAction) {
        return intentionAction.getText().startsWith(hint);
      }
    });
  }

  public IntentionAction findSingleIntention(@NotNull final String hint) {
    final List<IntentionAction> list = filterAvailableIntentions(hint);
    if (list.size() != 1) {
      Assert.fail(StringUtil.join(getAvailableIntentions(), new Function<IntentionAction, String>() {
        public String fun(final IntentionAction intentionAction) {
          return intentionAction.getText();
        }
      }, ", "));
    }
    return UsefulTestCase.assertOneElement(list);
  }

  public IntentionAction getAvailableIntention(final String intentionName, final String... filePaths) {
    List<IntentionAction> intentions = getAvailableIntentions(filePaths);
    return CodeInsightTestUtil.findIntentionByText(intentions, intentionName);
  }

  public void launchAction(@NotNull final IntentionAction action) {
    new WriteCommandAction(myProjectFixture.getProject()) {
      protected void run(final Result result) throws Exception {
        ShowIntentionActionsHandler.chooseActionAndInvoke(getFile(), getEditor(), action, action.getText());
      }
    }.execute().throwException();

  }

  public void testCompletion(final String[] filesBefore, final String fileAfter) {
    assertInitialized();
    configureByFiles(filesBefore);
    final LookupElement[] items = complete(CompletionType.BASIC);
    if (items != null) {
      System.out.println("items = " + Arrays.toString(items));
    }
    checkResultByFile(fileAfter);
  }

  protected void assertInitialized() {
    Assert.assertNotNull("setUp() hasn't been called", myPsiManager);
  }

  public void testCompletion(String fileBefore, String fileAfter, final String... additionalFiles) {
    testCompletion(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)), fileAfter);
  }

  public void testCompletionVariants(final String fileBefore, final String... expectedItems) {
    assertInitialized();
    final List<String> result = getCompletionVariants(fileBefore);
    UsefulTestCase.assertSameElements(result, expectedItems);
  }

  public List<String> getCompletionVariants(final String... filesBefore) {
    assertInitialized();
    configureByFiles(filesBefore);
    final LookupElement[] items = complete(CompletionType.BASIC);
    Assert.assertNotNull("No lookup was shown, probably there was only one lookup element that was inserted automatically", items);
    return getLookupElementStrings();
  }

  @Nullable
  public List<String> getLookupElementStrings() {
    assertInitialized();
    final LookupElement[] elements = getLookupElements();
    if (elements == null) return null;

    return ContainerUtil.map(elements, new Function<LookupElement, String>() {
      public String fun(final LookupElement lookupItem) {
        return lookupItem.getLookupString();
      }
    });
  }

  public void testRename(final String fileBefore, final String fileAfter, final String newName, final String... additionalFiles) {
    assertInitialized();
    configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)));
    testRename(fileAfter, newName);
  }

  public void testRename(final String fileAfter, final String newName) {
    renameElementAtCaret(newName);
    checkResultByFile(fileAfter);
  }

  @NotNull
  public PsiElement getElementAtCaret() {
    assertInitialized();
    final PsiElement element = TargetElementUtilBase.findTargetElement(getCompletionEditor(),
        TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assert element != null : "element not found in file " + myFile.getName() + " at caret position, offset " + myEditor.getCaretModel().getOffset();
    return element;
  }

  public void renameElementAtCaret(final String newName) {
    renameElement(getElementAtCaret(), newName);
  }

  public void renameElement(final PsiElement element, final String newName) {
    final boolean searchInComments = false;
    final boolean searchTextOccurrences = false;
    renameElement(element, newName, searchInComments, searchTextOccurrences);
  }

  public void renameElement(final PsiElement element,
                             final String newName,
                             final boolean searchInComments,
                             final boolean searchTextOccurrences) {
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {
      protected void run() throws Exception {
        final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, myEditor);
        new RenameProcessor(myProjectFixture.getProject(), substitution, newName, searchInComments, searchTextOccurrences).run();
     }
    }.execute().throwException();
  }

  public <T extends PsiElement> T findElementByText(String text, Class<T> elementClass) {
    int pos = PsiDocumentManager.getInstance(getProject()).getDocument(getFile()).getText().indexOf(text);
    assert pos >= 0: "text not found in file";
    return PsiTreeUtil.getParentOfType(getFile().findElementAt(pos), elementClass);
  }

  public void type(final char c) {
    assertInitialized();
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Exception {
        EditorActionManager actionManager = EditorActionManager.getInstance();
        final DataContext dataContext = DataManager.getInstance().getDataContext();
        if (c == '\b') {
          performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
          return;
        }
        if (c == '\n') {
          if (LookupManager.getActiveLookup(getEditor()) != null) {
            performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM);
            return;
          }

          performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
          return;
        }
        if (c == '\t') {
          if (LookupManager.getInstance(getProject()).getActiveLookup() != null) {
            performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
            return;
          }
        }

        actionManager.getTypedAction().actionPerformed(getEditor(), c, dataContext);
      }
    }.execute();
  }

  @Override
  public void type(String s) {
    for (int i = 0; i < s.length(); i++) {
      type(s.charAt(i));
    }
  }

  public void performEditorAction(final String actionId) {
    assertInitialized();
    final DataContext dataContext = DataManager.getInstance().getDataContext();
    EditorActionManager actionManager = EditorActionManager.getInstance();
    actionManager.getActionHandler(actionId).execute(getEditor(), dataContext);
  }

  @Override
  public Presentation testAction(AnAction action) {
    DataContext context = DataManager.getInstance().getDataContext();
    TestActionEvent e = new TestActionEvent(context, action);
    action.beforeActionPerformedUpdate(e);
    if (e.getPresentation().isVisible() && e.getPresentation().isVisible()) {
      action.actionPerformed(e);
    }
    return e.getPresentation();
  }

  public Collection<UsageInfo> testFindUsages(@NonNls final String... fileNames) {
    assertInitialized();
    configureByFiles(fileNames);
    final PsiElement targetElement = TargetElementUtilBase
      .findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assert targetElement != null : "Cannot find referenced element";
    return findUsages(targetElement);
  }

  public Collection<UsageInfo> findUsages(@NotNull final PsiElement targetElement) {
    final Project project = getProject();
    final FindUsagesHandler handler = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager().getFindUsagesHandler(targetElement, false);

    final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<UsageInfo>();
    assert handler != null : "Cannot find handler for: " + targetElement;
    final PsiElement[] psiElements = ArrayUtil.mergeArrays(handler.getPrimaryElements(), handler.getSecondaryElements(), PsiElement.class);
    final FindUsagesOptions options = handler.getFindUsagesOptions();
    for (PsiElement psiElement : psiElements) {
      handler.processElementUsages(psiElement, processor, options);
    }
    return processor.getResults();
  }

  public RangeHighlighter[] testHighlightUsages(final String... files) {
    configureByFiles(files);
    testAction(new HighlightUsagesAction());
    final Editor editor = getEditor();
    //final Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
    //assert editor != null;
    //HighlightUsagesHandler.invoke(getProject(), editor, getFile());
    return editor.getMarkupModel().getAllHighlighters();
  }

  public void moveFile(@NonNls final String filePath, @NonNls final String to, final String... additionalFiles) {
    assertInitialized();
    final Project project = myProjectFixture.getProject();
    new WriteCommandAction.Simple(project) {
      protected void run() throws Exception {
        configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, filePath)));
        final VirtualFile file = findFileInTempDir(to);
        assert file.isDirectory() : to + " is not a directory";
        final PsiDirectory directory = myPsiManager.findDirectory(file);
        new MoveFilesOrDirectoriesProcessor(project, new PsiElement[] {myFile}, directory,
                                            false, false, null, null).run();
      }
    }.execute().throwException();

  }

  @Nullable
  public GutterIconRenderer findGutter(final String filePath) {
    assertInitialized();
    final Project project = myProjectFixture.getProject();
    final Ref<GutterIconRenderer> result = new Ref<GutterIconRenderer>();
    new WriteCommandAction.Simple(project) {

      protected void run() throws Exception {
        configureByFilesInner(filePath);
        int offset = myEditor.getCaretModel().getOffset();

        final Collection<HighlightInfo> infos = doHighlighting();
        for (HighlightInfo info :infos) {
          if (info.endOffset >= offset && info.startOffset <= offset) {
            final GutterIconRenderer renderer = info.getGutterIconRenderer();
            if (renderer != null) {
              result.set(renderer);
              return;
            }
          }
        }

      }
    }.execute().throwException();
    return result.get();
  }

  @NotNull
  public Collection<GutterIconRenderer> findAllGutters(final String filePath) {
    assertInitialized();
    final Project project = myProjectFixture.getProject();
    final SortedMap<Integer, List<GutterIconRenderer>> result = new TreeMap<Integer, List<GutterIconRenderer>>();
    new WriteCommandAction.Simple(project) {

      protected void run() throws Exception {
        configureByFilesInner(filePath);

        for (HighlightInfo info : doHighlighting()) {
          addGutterIconRenderer(info.getGutterIconRenderer(), info.startOffset);
        }

        for (final RangeHighlighter highlighter : myEditor.getDocument().getMarkupModel(project).getAllHighlighters()) {
          addGutterIconRenderer(highlighter.getGutterIconRenderer(), highlighter.getStartOffset());
        }
      }

      private void addGutterIconRenderer(final GutterIconRenderer renderer, final int offset) {
        if (renderer == null) return;

        List<GutterIconRenderer> renderers = result.get(offset);
        if (renderers == null) {
          result.put(offset, renderers = new SmartList<GutterIconRenderer>());
        }
        renderers.add(renderer);
      }

    }.execute().throwException();
    return ContainerUtil.concat(result.values());
  }


  public PsiFile addFileToProject(@NonNls final String relativePath, @NonNls final String fileText) {
    assertInitialized();
    return addFileToProject(getTempDirPath(), relativePath, fileText);
  }

  protected PsiFile addFileToProject(String rootPath, String relativePath, String fileText) {
    try {
      if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
        final VirtualFile file = myTempDirFixture.createFile(relativePath, fileText);
        return PsiManager.getInstance(getProject()).findFile(file);
      }

      return ((HeavyIdeaTestFixture)myProjectFixture).addFileToProject(rootPath, relativePath, fileText);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> void registerExtension(final ExtensionsArea area, final ExtensionPointName<T> epName, final T extension) {
    assertInitialized();
    final ExtensionPoint<T> extensionPoint = area.getExtensionPoint(epName);
    extensionPoint.registerExtension(extension);
    disposeOnTearDown(new Disposable() {
      public void dispose() {
        extensionPoint.unregisterExtension(extension);
      }
    });
  }

  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  @Override
  public LookupElement[] complete(CompletionType type) {
    return complete(type, 1);
  }

  @Override
  public LookupElement[] complete(final CompletionType type, final int invocationCount) {
    assertInitialized();
    myEmptyLookup = false;
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Exception {
        final CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(type) {
          protected PsiFile createFileCopy(final PsiFile file) {
            final PsiFile copy = super.createFileCopy(file);
            if (myFileContext != null) {
              final PsiElement contextCopy = myFileContext.copy();
              final PsiFile containingFile = contextCopy.getContainingFile();
              if (containingFile instanceof PsiFileImpl) {
                ((PsiFileImpl)containingFile).setOriginalFile(myFileContext.getContainingFile());
              }
              setContext(copy, contextCopy);
            }
            return copy;
          }

          @Override
          protected void completionFinished(final int offset1, final int offset2, final CompletionContext context, final CompletionProgressIndicator indicator,
                                            final LookupElement[] items) {
            myEmptyLookup = items.length == 0;
            super.completionFinished(offset1, offset2, context, indicator, items);
          }
        };
        Editor editor = getCompletionEditor();
        handler.invokeCompletion(getProject(), editor, PsiUtilBase.getPsiFileInEditor(editor, getProject()), invocationCount);
      }
    }.execute();
    return getLookupElements();
  }

  @Nullable
  protected Editor getCompletionEditor() {
    return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myFile);
  }

  @Nullable
  public LookupElement[] completeBasic() {
    return complete(CompletionType.BASIC);
  }

  @Nullable
  public LookupElement[] getLookupElements() {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    if (lookup == null) {
      return myEmptyLookup ? LookupElement.EMPTY_ARRAY : null;
    }
    else {
      final List<LookupElement> list = lookup.getItems();
      return list.toArray(new LookupElement[list.size()]);
    }
  }

  public void checkResult(final String text) {
    checkResult(text, false);
  }

  public void checkResult(String text, boolean stripTrailingSpaces) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    EditorUtil.fillVirtualSpaceUntilCaret(myEditor);
    checkResult("TEXT", stripTrailingSpaces, SelectionAndCaretMarkupLoader.fromText(text, getProject()), myFile.getText());
  }

  public void checkResultByFile(final String expectedFile) {
    checkResultByFile(expectedFile, false);
  }

  public void checkResultByFile(final String expectedFile, final boolean ignoreTrailingWhitespaces) {
    assertInitialized();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Exception {
        checkResultByFile(expectedFile, myFile, ignoreTrailingWhitespaces);
      }
    }.execute().throwException();
  }

  public void checkResultByFile(final String filePath, final String expectedFile, final boolean ignoreTrailingWhitespaces) {
    assertInitialized();

    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Exception {
        final String path = filePath.replace(File.separatorChar, '/');
        final VirtualFile copy = findFileInTempDir(path);
        if (copy == null) {
          throw new IllegalArgumentException("could not find results file " + path);
        }
        final PsiFile psiFile = myPsiManager.findFile(copy);
        assert psiFile != null;
        checkResultByFile(expectedFile, psiFile, ignoreTrailingWhitespaces);
      }
    }.execute().throwException();
  }

  public void setUp() throws Exception {
    super.setUp();

    myProjectFixture.setUp();
    myTempDirFixture.setUp();
    myPsiManager = (PsiManagerImpl)PsiManager.getInstance(getProject());
    configureInspections(myInspections == null ? new LocalInspectionTool[0] : myInspections);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);
    DaemonCodeAnalyzer.getInstance(getProject()).setUpdateByTimerEnabled(false);
    ensureIndexesUpToDate(getProject());
  }

  private void enableInspectionTool(InspectionProfileEntry tool){
    final String shortName = tool.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null){
      String id = tool instanceof LocalInspectionTool ? ((LocalInspectionTool)tool).getID() : shortName;
      HighlightDisplayKey.register(shortName, tool.getDisplayName(), id);
    }
    myAvailableTools.put(shortName, tool);
    myAvailableLocalTools.put(shortName, tool instanceof LocalInspectionTool ?
                                         new LocalInspectionToolWrapper((LocalInspectionTool)tool) :
                                         (InspectionTool)tool);
  }

  private void configureInspections(final InspectionProfileEntry[] tools) {
    for (InspectionProfileEntry tool : tools) {
      enableInspectionTool(tool);
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE) {
      @NotNull
      public ModifiableModel getModifiableModel() {
        mySource = this;
        return this;
      }

      @NotNull
      public InspectionProfileEntry[] getInspectionTools(PsiElement element) {
        final Collection<InspectionTool> tools = myAvailableLocalTools.values();
        return tools.toArray(new InspectionTool[tools.size()]);
      }

      @Override
      public List<ToolsImpl> getAllEnabledInspectionTools() {
        List<ToolsImpl> result = new ArrayList<ToolsImpl>();
        for (InspectionProfileEntry entry : getInspectionTools(null)) {
          result.add(new ToolsImpl(entry, entry.getDefaultLevel(), true));
        }
        return result;
      }

      public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
        return key != null && key.toString() != null && myAvailableTools.containsKey(key.toString());
      }

      public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, PsiElement element) {
        final InspectionProfileEntry entry = myAvailableTools.get(key.toString());
        return entry != null ? entry.getDefaultLevel() : HighlightDisplayLevel.WARNING;
      }

      public InspectionTool getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
        return myAvailableLocalTools.get(shortName);
      }
    };
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    inspectionProfileManager.addProfile(profile);
    Disposer.register(getTestRootDisposable(), new Disposable() {
      public void dispose() {
        inspectionProfileManager.deleteProfile(PROFILE);
      }
    });
    inspectionProfileManager.setRootProfile(profile.getName());
    InspectionProjectProfileManager.getInstance(getProject()).updateProfile(profile);
    InspectionProjectProfileManager.getInstance(getProject()).setProjectProfile(profile.getName());
  }

  public void tearDown() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      LookupManager.getInstance(getProject()).hideActiveLookup();
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        public void run() {
          LookupManager.getInstance(getProject()).hideActiveLookup();
        }
      }, ModalityState.NON_MODAL);
      UIUtil.pump();
    }

    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      editorManager.closeFile(openFile);
    }

    myProjectFixture.tearDown();
    myTempDirFixture.tearDown();

    super.tearDown();
  }

  private PsiFile configureByFilesInner(@NonNls String... filePaths) throws IOException {
    assertInitialized();
    myFile = null;
    myEditor = null;
    for (int i = filePaths.length - 1; i > 0; i--) {
      configureByFileInner(filePaths[i]);
    }
    return configureByFileInner(filePaths[0]);
  }

  public PsiFile configureByFile(final String file) {
    assertInitialized();
    new WriteCommandAction.Simple(getProject()) {
      protected void run() throws Exception {
        configureByFilesInner(file);
      }
    }.execute();
    return myFile;
  }

  public void configureByFiles(@NonNls final String... files) {
    new WriteCommandAction.Simple(getProject()) {
      protected void run() throws Exception {
        configureByFilesInner(files);
      }
    }.execute();
  }

  public PsiFile configureByText(final FileType fileType, @NonNls final String text) {
    assertInitialized();
    final String extension = fileType.getDefaultExtension();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager.getFileTypeByExtension(extension) != fileType) {
      new WriteCommandAction(getProject()) {
        protected void run(Result result) throws Exception {
          fileTypeManager.associateExtension(fileType, extension);
        }
      }.execute();
    }
    final String fileName = "aaa." + extension;
    return configureByText(fileName, text);
  }

  public PsiFile configureByText(String fileName, @NonNls String text) {
    assertInitialized();
    try {
      final VirtualFile vFile;
      if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
        final VirtualFile root = LightPlatformTestCase.getSourceRoot();
        root.refresh(false, false);
        vFile = root.findOrCreateChildData(this, fileName);
      }
      else{
        String prefix = StringUtil.getPackageName(fileName);
        if (prefix.length() < 3) {
          prefix += "___";
        }
        final File tempFile = File.createTempFile(prefix, "." + StringUtil.getShortName(fileName), new File(getTempDirPath()));
        vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
      }
      VfsUtil.saveText(vFile, text);
      configureInner(vFile, SelectionAndCaretMarkupLoader.fromFile(vFile, getProject()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return myFile;
  }

  public Document getDocument(final PsiFile file) {
    assertInitialized();
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  public void setFileContext(@Nullable final PsiElement context) {
    myFileContext = context;
    setContext(myFile, context);
  }

  /**
   *
   * @param filePath
   * @throws IOException
   */
  private PsiFile configureByFileInner(@NonNls String filePath) {
    assertInitialized();
    final VirtualFile file = copyFileToProject(filePath);
    return configureByFileInner(file);
  }

  public PsiFile configureFromTempProjectFile(final String filePath)  {
    return configureByFileInner(findFileInTempDir(filePath));
  }

  public void configureFromExistingVirtualFile(VirtualFile f) {
    configureByFileInner(f);
  }

  private PsiFile configureByFileInner(final VirtualFile copy) {
    return configureInner(copy, SelectionAndCaretMarkupLoader.fromFile(copy, getProject()));
  }

  private PsiFile configureInner(@NotNull final VirtualFile copy, final SelectionAndCaretMarkupLoader loader) {
    assertInitialized();
    try {
      final OutputStream outputStream = copy.getOutputStream(null, 0, 0);
      outputStream.write(loader.newFileText.getBytes(copy.getCharset()));
      outputStream.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    myFile = myPsiManager.findFile(copy);
    setContext(myFile, myFileContext);
    myEditor = createEditor(copy);
    assert myEditor != null : "Editor couldn't be created for file: " + copy.getPath() + ", use copyFileToProject(..) method for this file instead of configureByFile(..)" ;
    int offset = -1;
    if (loader.caretMarker != null) {
      offset = loader.caretMarker.getStartOffset();
      myEditor.getCaretModel().moveToOffset(offset);
    }
    if (loader.selStartMarker != null && loader.selEndMarker != null) {
      myEditor.getSelectionModel().setSelection(loader.selStartMarker.getStartOffset(), loader.selEndMarker.getStartOffset());
    }

    Module module = getModule();
    if (module != null) {
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        module.getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
      }
    }

    return myFile;
  }

  private static void setContext(final PsiFile file, final PsiElement context) {
    if (file != null && context != null) {
      file.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, new IdentitySmartPointer<PsiElement>(context));
    }
  }

  public VirtualFile findFileInTempDir(final String filePath) {
    if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
      return myTempDirFixture.getFile(filePath);
    }
    String fullPath = getTempDirPath() + "/" + filePath;

    final VirtualFile copy = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'));
    assert copy != null : "file " + fullPath + " not found";
    return copy;
  }

  @Nullable
  private Editor createEditor(VirtualFile file) {
    final Project project = getProject();
    final FileEditorManager instance = FileEditorManager.getInstance(project);
    if (file.getFileType().isBinary()) {
      return null;
    }
    return instance.openTextEditor(new OpenFileDescriptor(project, file, 0), false);
  }

  private void collectAndCheckHighlightings(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, Ref<Long> duration)
    throws Exception {
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, myFile);

    collectAndCheckHighlightings(data, duration);
  }

  private void collectAndCheckHighlightings(final ExpectedHighlightingData data, final Ref<Long> duration) {
    final Project project = getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ((PsiFileImpl)myFile).calcTreeElement(); //to load text

    //to initialize caches
    myPsiManager.getCacheManager().getFilesWithWord(XXX, UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(project), true);

    List<HighlightInfo> infos;
    try {
      ((PsiManagerImpl)PsiManager.getInstance(project)).setAssertOnFileLoadingFilter(myJavaFilesFilter);

      final long start = System.currentTimeMillis();
//    ProfilingUtil.startCPUProfiling();
      infos = doHighlighting();
      removeDuplicatedRangesForInjected(infos);
      final long elapsed = System.currentTimeMillis() - start;
      duration.set(duration.isNull()? elapsed : duration.get().longValue() + elapsed);
//    ProfilingUtil.captureCPUSnapshot("testing");
    }
    finally {
      ((PsiManagerImpl)PsiManager.getInstance(project)).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);
    }


    data.checkResult(infos, myEditor.getDocument().getText());
  }

  private static void removeDuplicatedRangesForInjected(List<HighlightInfo> infos) {
    Collections.sort(infos, new Comparator<HighlightInfo>() {
      public int compare(HighlightInfo o1, HighlightInfo o2) {
        final int i = o2.startOffset - o1.startOffset;
        return i != 0 ? i : o1.getSeverity().myVal - o2.getSeverity().myVal;
      }
    });
    HighlightInfo prevInfo = null;
    for (Iterator<HighlightInfo> it = infos.iterator(); it.hasNext();) {
      final HighlightInfo info = it.next();
      if (prevInfo != null &&
          info.getSeverity() == HighlightSeverity.INFORMATION &&
          info.description == null &&
          info.startOffset == prevInfo.startOffset &&
          info.endOffset == prevInfo.endOffset) {
        it.remove();
      }
      prevInfo = info.getSeverity() == HighlightInfoType.INJECTED_FRAGMENT_SEVERITY ? info : null;
    }
  }

  @NotNull
  public List<HighlightInfo> doHighlighting() {
    final Project project = myProjectFixture.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    return
    ApplicationManager.getApplication().runReadAction(new Computable<List<HighlightInfo>>() {
      public List<HighlightInfo> compute() {
        return instantiateAndRun(getFile(), getEditor(), ArrayUtil.EMPTY_INT_ARRAY, myAllowDirt);
      }
    });
  }

  @NotNull
  public static List<HighlightInfo> instantiateAndRun(@NotNull PsiFile file, @NotNull Editor editor, @NotNull int[] toIgnore, boolean allowDirt) {
    Project project = file.getProject();
    ensureIndexesUpToDate(project);
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzerImpl.getInstance(project);
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    return codeAnalyzer.runPasses(file, editor.getDocument(), textEditor, new DaemonProgressIndicator(), toIgnore, allowDirt, true);
  }

  public static void ensureIndexesUpToDate(Project project) {
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, null);
    FileBasedIndex.getInstance().ensureUpToDate(TodoIndex.NAME, project, null);
    assertTrue(!DumbServiceImpl.getInstance(project).isDumb());
  }

  public String getTestDataPath() {
    return myTestDataPath;
  }

  public Project getProject() {
    return myProjectFixture.getProject();
  }

  public Module getModule() {
    return myProjectFixture.getModule();
  }

  public Editor getEditor() {
    return myEditor;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public static List<IntentionAction> getAvailableIntentions(final Editor editor, final PsiFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<IntentionAction>>() {
      public List<IntentionAction> compute() {
        return doGetAvailableIntentions(editor, file);
      }
    });
  }

  private static List<IntentionAction> doGetAvailableIntentions(Editor editor, PsiFile file) {
    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    ShowIntentionsPass.getActionsToShow(editor, file, intentions, -1);
    List<HighlightInfo.IntentionActionDescriptor> descriptors = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    descriptors.addAll(intentions.intentionsToShow);
    descriptors.addAll(intentions.errorFixesToShow);
    descriptors.addAll(intentions.inspectionFixesToShow);
    descriptors.addAll(intentions.guttersToShow);

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    List<IntentionAction> result = new ArrayList<IntentionAction>();

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getFileLevelHighlights(file.getProject(), file);
    for (HighlightInfo info : infos) {
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
        HighlightInfo.IntentionActionDescriptor actionInGroup = pair.first;
        if (actionInGroup.getAction().isAvailable(file.getProject(), editor, file)) {
          descriptors.add(actionInGroup);
        }
      }
    }

    // add all intention options for simplicity
    for (HighlightInfo.IntentionActionDescriptor descriptor : descriptors) {
      result.add(descriptor.getAction());
      List<IntentionAction> options = descriptor.getOptions(element);
      if (options != null) {
        for (IntentionAction option : options) {
          if (option.isAvailable(file.getProject(), editor, file)) {
            result.add(option);
          }
        }
      }
    }
    return result;
  }

  public void allowTreeAccessForFile(final VirtualFile file) {
    myJavaFilesFilter.allowTreeAccessForFile(file);
  }

  public void allowTreeAccessForAllFiles() {
    myJavaFilesFilter.allowTreeAccessForAllFiles();
  }

  static class SelectionAndCaretMarkupLoader {
    final String newFileText;
    final RangeMarker caretMarker;
    final RangeMarker selStartMarker;
    final RangeMarker selEndMarker;

    static SelectionAndCaretMarkupLoader fromFile(String path, Project project) throws IOException {
      return new SelectionAndCaretMarkupLoader(StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(new File(path)))), project);
    }
    static SelectionAndCaretMarkupLoader fromFile(VirtualFile file, Project project) {
      final String text;
      try {
        text = VfsUtil.loadText(file);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return new SelectionAndCaretMarkupLoader(StringUtil.convertLineSeparators(text), project);
    }

    static SelectionAndCaretMarkupLoader fromText(String text, Project project) {
      return new SelectionAndCaretMarkupLoader(text, project);
    }

    private SelectionAndCaretMarkupLoader(String fileText, Project project) {
      final Document document = EditorFactory.getInstance().createDocument(fileText);

      int caretIndex = fileText.indexOf(CARET_MARKER);
      int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
      int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

      caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
      selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
      selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

      new WriteCommandAction(project) {
        protected void run(Result result) throws Exception {
          if (caretMarker != null) {
            document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
          }
          if (selStartMarker != null) {
            document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
          }
          if (selEndMarker != null) {
            document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
          }
        }
      }.execute();

      newFileText = document.getText();
    }

  }
  private void checkResultByFile(@NonNls String expectedFile,
                                 @NotNull PsiFile originalFile,
                                 boolean stripTrailingSpaces) throws IOException {
    if (!stripTrailingSpaces) {
      EditorUtil.fillVirtualSpaceUntilCaret(myEditor);
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    checkResult(expectedFile, stripTrailingSpaces, SelectionAndCaretMarkupLoader.fromFile(getTestDataPath() + "/" + expectedFile, getProject()), originalFile.getText());
  }

  private void checkResult(final String expectedFile,
                           final boolean stripTrailingSpaces,
                           final SelectionAndCaretMarkupLoader loader,
                           String actualText) {
    assertInitialized();
    Project project = myProjectFixture.getProject();

    project.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    if (stripTrailingSpaces) {
      actualText = stripTrailingSpaces(actualText);
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    String newFileText1 = loader.newFileText;
    if (stripTrailingSpaces) {
      newFileText1 = stripTrailingSpaces(newFileText1);
    }

    actualText = StringUtil.convertLineSeparators(actualText);

    //noinspection HardCodedStringLiteral
    Assert.assertEquals("Text mismatch in file " + expectedFile, newFileText1, actualText);

    if (loader.caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.caretMarker.getStartOffset());
      int caretCol = loader.caretMarker.getStartOffset() - StringUtil.lineColToOffset(loader.newFileText, caretLine, 0);

      Assert.assertEquals("caretLine in " + expectedFile, caretLine + 1, myEditor.getCaretModel().getLogicalPosition().line + 1);
      Assert.assertEquals("caretColumn in " + expectedFile, caretCol + 1, myEditor.getCaretModel().getLogicalPosition().column + 1);
    }

    if (loader.selStartMarker != null && loader.selEndMarker != null) {
      int selStartLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.selStartMarker.getStartOffset());
      int selStartCol = loader.selStartMarker.getStartOffset() - StringUtil.lineColToOffset(loader.newFileText, selStartLine, 0);

      int selEndLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.selEndMarker.getEndOffset());
      int selEndCol = loader.selEndMarker.getEndOffset() - StringUtil.lineColToOffset(loader.newFileText, selEndLine, 0);

      Assert.assertEquals("selectionStartLine in " + expectedFile, selStartLine + 1,
                          StringUtil.offsetToLineNumber(loader.newFileText, myEditor.getSelectionModel().getSelectionStart()) + 1);

      Assert.assertEquals("selectionStartCol in " + expectedFile, selStartCol + 1, myEditor.getSelectionModel().getSelectionStart() -
                                                                StringUtil.lineColToOffset(loader.newFileText, selStartLine, 0) + 1);

      Assert.assertEquals("selectionEndLine in " + expectedFile, selEndLine + 1,
                          StringUtil.offsetToLineNumber(loader.newFileText, myEditor.getSelectionModel().getSelectionEnd()) + 1);

      Assert.assertEquals("selectionEndCol in " + expectedFile, selEndCol + 1,
                          myEditor.getSelectionModel().getSelectionEnd() - StringUtil.lineColToOffset(loader.newFileText, selEndLine, 0) +
                          1);
    }
    else if (myEditor != null) {
      Assert.assertTrue("has no selection in " + expectedFile, !myEditor.getSelectionModel().hasSelection());
    }
  }

  private static String stripTrailingSpaces(String actualText) {
    final Document document = EditorFactory.getInstance().createDocument(actualText);
    ((DocumentEx)document).stripTrailingSpaces(false);
    actualText = document.getText();
    return actualText;
  }

  public void canChangeDocumentDuringHighlighting(boolean canI) {
    myAllowDirt = canI;
  }
}
