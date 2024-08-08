// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.analysis.AnalysisScope;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.highlighting.actions.HighlightUsagesAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInsight.intention.impl.IntentionListStep;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.actions.CleanupInspectionIntention;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.find.FindManager;
import com.intellij.find.actions.SearchTarget2UsageTarget;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.api.UsageOptions;
import com.intellij.find.usages.impl.AllSearchOptions;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.impl.ReferencesKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryTableTracker;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.rename.*;
import com.intellij.refactoring.rename.api.RenameTarget;
import com.intellij.refactoring.rename.impl.RenameKt;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.utils.inlays.CaretAndInlaysInfo;
import com.intellij.testFramework.utils.inlays.InlayHintsChecker;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.util.ui.UIUtil;
import kotlin.UninitializedPropertyAccessException;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.find.usages.impl.ImplKt.buildUsageViewQuery;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.testFramework.RunAll.runAll;
import static com.intellij.testFramework.UsefulTestCase.assertOneElement;
import static com.intellij.util.ObjectUtils.coalesce;
import static org.junit.Assert.*;

/**
 * @author Dmitry Avdeev
 */
@TestOnly
public class CodeInsightTestFixtureImpl extends BaseFixture implements CodeInsightTestFixture {
  private static final Logger LOG = Logger.getInstance(CodeInsightTestFixtureImpl.class);
  private static final Function<IntentionAction, String> INTENTION_NAME_FUN = intentionAction -> '"' + intentionAction.getText() + '"';

  private static final String RAINBOW = "rainbow";
  private static final String FOLD = "fold";

  private final IdeaProjectTestFixture myProjectFixture;
  private final TempDirTestFixture myTempDirFixture;
  private PsiManagerImpl myPsiManager;
  private VirtualFile myFile;

  // Strong references to PSI files configured by the test (to avoid tree access assertions after PSI has been GC'ed)
  @SuppressWarnings("unused") private PsiFile myPsiFile;
  private PsiFile[] myAllPsiFiles;

  private Editor editor;
  private EditorTestFixture myEditorTestFixture;
  private String myTestDataPath;
  private VirtualFileFilter myVirtualFileFilter = new FileTreeAccessFilter();
  private boolean myAllowDirt;
  private boolean caresAboutInjection = true;
  private boolean myReadEditorMarkupModel;
  private VirtualFilePointerTracker myVirtualFilePointerTracker;
  private LibraryTableTracker  myLibraryTableTracker;
  private SelectionAndCaretMarkupApplyPolicy mySelectionAndCaretMarkupApplyPolicy = SelectionAndCaretMarkupApplyPolicy.UPDATE_FILE_AND_KEEP_DOCUMENT_CLEAN;

  public CodeInsightTestFixtureImpl(@NotNull IdeaProjectTestFixture projectFixture, @NotNull TempDirTestFixture tempDirTestFixture) {
    myProjectFixture = projectFixture;
    myTempDirFixture = tempDirTestFixture;
  }

  private void setFileAndEditor(@NotNull VirtualFile file, @NotNull Editor editor) {
    myFile = file;
    this.editor = editor;
    myEditorTestFixture = new EditorTestFixture(getProject(), editor, file);
    myPsiFile = ReadAction.compute(() -> PsiManager.getInstance(getProject()).findFile(myFile));
  }

  private void clearFileAndEditor() {
    myFile = null;
    editor = null;
    myEditorTestFixture = null;
    myPsiFile = null;
    myAllPsiFiles = null;
  }

  private static void addGutterIconRenderer(GutterMark renderer, int offset, @NotNull Map<? super Integer, List<GutterMark>> result) {
    if (renderer == null) return;

    List<GutterMark> renderers = result.computeIfAbsent(offset, __ -> new SmartList<>());
    renderers.add(renderer);
  }

  private static void removeDuplicatedRangesForInjected(@NotNull List<? extends HighlightInfo> infos) {
    infos.sort((o1, o2) -> {
      int i = o1.startOffset - o2.startOffset;
      return i != 0 ? i : o1.getSeverity().myVal - o2.getSeverity().myVal;
    });
    HighlightInfo prevInfo = null;
    for (Iterator<? extends HighlightInfo> it = infos.iterator(); it.hasNext();) {
      HighlightInfo info = it.next();
      if (prevInfo != null &&
          info.getSeverity() == HighlightInfoType.SYMBOL_TYPE_SEVERITY &&
          info.getDescription() == null &&
          info.startOffset == prevInfo.startOffset &&
          info.endOffset == prevInfo.endOffset) {
        it.remove();
      }
      prevInfo = info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT ? info : null;
    }
  }

  @NotNull
  @TestOnly
  public static List<HighlightInfo> instantiateAndRun(@NotNull PsiFile file,
                                                      @NotNull Editor editor,
                                                      int @NotNull [] toIgnore,
                                                      boolean canChangeDocument) {
    return instantiateAndRun(file, editor, toIgnore, canChangeDocument, false);
  }

  @NotNull
  @TestOnly
  public static List<HighlightInfo> instantiateAndRun(@NotNull PsiFile psiFile,
                                                      @NotNull Editor editor,
                                                      int @NotNull [] toIgnore,
                                                      boolean canChangeDocument,
                                                      boolean readEditorMarkupModel) {
    Project project = psiFile.getProject();
    ensureIndexesUpToDate(project);
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (!ReadAction.compute(() -> ProblemHighlightFilter.shouldHighlightFile(psiFile))) {
      boolean inSource = ReadAction.compute(() -> ProjectRootManager.getInstance(project).getFileIndex().isInSource(virtualFile));
      throw new IllegalStateException("ProblemHighlightFilter.shouldHighlightFile('" + psiFile + "') == false, so can't highlight it." +
                                      (inSource ? "" : " Maybe it's because " + virtualFile+ " is outside source folders? (source folders: " +
                                      ReadAction.compute(() -> Arrays.toString(ProjectRootManager.getInstance(project).getContentSourceRoots()))+")"));
    }

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    ProjectInspectionProfileManager.getInstance(project); // avoid "severities changed, restart" event

    ProcessCanceledException exception = null;
    int retries = 1000;
    for (int i = 0; i < retries; i++) {
      int oldDelay = settings.getAutoReparseDelay();
      try {
        settings.setAutoReparseDelay(0);
        List<HighlightInfo> infos = new ArrayList<>();
        EdtTestUtil.runInEdtAndWait(() -> {
          codeAnalyzer.runPasses(psiFile, editor.getDocument(), textEditor, toIgnore, canChangeDocument, null);
          IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
          if (policy != null) {
            policy.waitForHighlighting(project, editor);
          }
          IdentifierHighlighterPassFactory.waitForIdentifierHighlighting();
          UIUtil.dispatchAllInvocationEvents();
          Segment focusModeRange = (editor instanceof EditorImpl) ? ((EditorImpl)editor).getFocusModeRange() : null;
          int startOffset = focusModeRange != null ? focusModeRange.getStartOffset() : 0;
          int endOffset = focusModeRange != null ? focusModeRange.getEndOffset() : editor.getDocument().getTextLength();
          DaemonCodeAnalyzerEx.processHighlights(editor.getDocument(), project, null, startOffset, endOffset,
                                                 Processors.cancelableCollectProcessor(infos));
          if (readEditorMarkupModel) {
            MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
            DaemonCodeAnalyzerEx.processHighlights(markupModel, project, null, startOffset, endOffset,
                                                   Processors.cancelableCollectProcessor(infos));
          }
        });
        return infos;
      }
      catch (ProcessCanceledException e) {
        Throwable cause = e.getCause();
        if (cause != null && cause.getClass() != Throwable.class) {
          // canceled because of an exception, no need to repeat the same
          throw e;
        }

        EdtTestUtil.runInEdtAndWait(() -> {
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          UIUtil.dispatchAllInvocationEvents();
        });
        exception = e;
      }
      finally {
        settings.setAutoReparseDelay(oldDelay);
      }
    }
    throw new AssertionError("Unable to highlight after " + retries + " retries", exception);
  }

  public static void ensureIndexesUpToDate(@NotNull Project project) {
    IndexingTestUtil.waitUntilIndexesAreReady(project);
    if (!DumbService.isDumb(project)) {
      ReadAction.run(() -> {
        for (FileBasedIndexExtension<?,?> extension : FileBasedIndexExtension.EXTENSION_POINT_NAME.getExtensionList()) {
          FileBasedIndex.getInstance().ensureUpToDate(extension.getName(), project, null);
        }
      });
    }
  }

  @NotNull
  @TestOnly
  public static List<IntentionAction> getAvailableIntentions(@NotNull Editor editor, @NotNull PsiFile file) {
    IdeaTestExecutionPolicy current = IdeaTestExecutionPolicy.current();
    if (current != null) {
      current.waitForHighlighting(file.getProject(), editor);
    }
    waitForUnresolvedReferencesQuickFixesUnderCaret(file, editor);
    List<IntentionAction> result = new ArrayList<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      IntentionListStep intentionListStep = getIntentionListStep(editor, file);
      for (Map.Entry<IntentionAction, List<IntentionAction>> entry : intentionListStep.getActionsWithSubActions().entrySet()) {
        result.add(entry.getKey());
        result.addAll(entry.getValue());
      }
    });
    return result;
  }

  @RequiresEdt
  @NotNull
  private static IntentionListStep getIntentionListStep(@NotNull Editor editor, @NotNull PsiFile file) {
    CachedIntentions cachedIntentions = ShowIntentionActionsHandler.calcCachedIntentions(file.getProject(), editor, file);
    return new IntentionListStep(null, editor, file, file.getProject(), cachedIntentions);
  }

  public static void waitForUnresolvedReferencesQuickFixesUnderCaret(@NotNull PsiFile file, @NotNull Editor editor) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      assert !ApplicationManager.getApplication().isWriteAccessAllowed(): "must not call under write action";

      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        if (!ReadAction.compute(() -> file.getProject().isDisposed() || editor.isDisposed())) {
          DaemonCodeAnalyzerImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(file, editor);
        }
      });
      try {
        while (!future.isDone()) {
          try {
            future.get(10, TimeUnit.MILLISECONDS);
          }
          catch (TimeoutException ignored) {
          }
          UIUtil.dispatchAllInvocationEvents();
        }
        future.get();
      }
      catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      DaemonCodeAnalyzerImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(file, editor);
    }
  }

  @NotNull
  @Override
  public String getTempDirPath() {
    return myTempDirFixture.getTempDirPath();
  }

  @NotNull
  @Override
  public TempDirTestFixture getTempDirFixture() {
    return myTempDirFixture;
  }

  @NotNull
  @Override
  public VirtualFile copyFileToProject(@NotNull String sourcePath) {
    return copyFileToProject(sourcePath, sourcePath);
  }

  @NotNull
  @Override
  public VirtualFile copyFileToProject(@NotNull String sourcePath, @NotNull String targetPath) {
    String testDataPath = getTestDataPath();
    File sourceFile = new File(testDataPath, toSystemDependentName(sourcePath));
    if (!sourceFile.exists()) {
      File candidate = new File(sourcePath);
      if (candidate.isAbsolute()) {
        sourceFile = candidate;
        if (FileUtil.pathsEqual(targetPath, sourcePath)) {
          Path testDataPathObj = Paths.get(testDataPath);
          Path targetPathObj = Paths.get(targetPath);
          if (targetPathObj.startsWith(testDataPathObj) && !targetPathObj.equals(testDataPathObj)) {
            targetPath = testDataPathObj.relativize(targetPathObj).toString();
          }
          else {
            throw new IllegalArgumentException("Cannot guess target path for '" + sourcePath + "'; please specify explicitly");
          }
        }
      }
    }

    targetPath = FileUtil.toSystemIndependentName(targetPath);

    VirtualFile targetFile = myTempDirFixture.getFile(targetPath);

    if (!sourceFile.exists() && targetFile != null && targetPath.equals(sourcePath)) {
      return targetFile;
    }

    assertFileEndsWithCaseSensitivePath(sourceFile);

    assertTrue("Cannot find source file: " + sourceFile + "; test data path: " + testDataPath+"; sourcePath: "+sourcePath, sourceFile.exists());
    assertTrue("Not a file: " + sourceFile, sourceFile.isFile());

    if (targetFile == null) {
      targetFile = myTempDirFixture.createFile(targetPath);
      VfsTestUtil.assertFilePathEndsWithCaseSensitivePath(targetFile, sourcePath);
      targetFile.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, sourceFile.getAbsolutePath());
    }

    copyContent(sourceFile, targetFile);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());

    return targetFile;
  }

  private static void assertFileEndsWithCaseSensitivePath(@NotNull File sourceFile) {
    try {
      String sourceName = sourceFile.getPath();
      File realFile = sourceFile.getCanonicalFile();
      String realFileName = realFile.getPath();
      if (!sourceName.equals(realFileName) && sourceName.equalsIgnoreCase(realFileName)) {
        fail("Please correct case-sensitivity of path to prevent test failure on case-sensitive file systems:\n" +
             "     path " + sourceFile.getPath() + "\n" +
             "real path " + realFile.getPath());
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException("sourceFile="+sourceFile, e);
    }
  }

  private static void copyContent(@NotNull File sourceFile, @NotNull VirtualFile targetFile) {
    try {
      WriteAction.runAndWait(() -> {
        targetFile.setBinaryContent(FileUtil.loadFileBytes(sourceFile));
        // update the document now, otherwise MemoryDiskConflictResolver will do it later at unexpected moment of time
        FileDocumentManager.getInstance().reloadFiles(targetFile);
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  @Override
  public VirtualFile copyDirectoryToProject(@NotNull String sourcePath, @NotNull String targetPath) {
    String testDataPath = getTestDataPath();

    File fromFile = new File(testDataPath + "/" + sourcePath);
    if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
      VirtualFile file = myTempDirFixture.copyAll(fromFile.getPath(), targetPath);
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
      return file;
    }

    File targetFile = new File(getTempDirPath() + "/" + targetPath);
    try {
      FileUtil.copyDir(fromFile, targetFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile);
    assertNotNull(file);
    file.refresh(false, true);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());

    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    if (policy != null) {
      PsiDirectory directory = ReadAction.compute(() -> PsiManager.getInstance(getProject()).findDirectory(file));
      assertNotNull(directory);
      policy.testDirectoryConfigured(directory);
    }

    return file;
  }

  @Override
  public void enableInspections(InspectionProfileEntry @NotNull ... inspections) {
    assertInitialized();
    InspectionsKt.enableInspectionTools(getProject(), myProjectFixture.getTestRootDisposable(), inspections);
  }

  @SafeVarargs
  @Override
  public final void enableInspections(Class<? extends LocalInspectionTool> @NotNull ... inspections) {
    enableInspections(Arrays.asList(inspections));
  }

  @Override
  public void enableInspections(@NotNull Collection<Class<? extends LocalInspectionTool>> inspections) {
    List<InspectionProfileEntry> tools = InspectionTestUtil.instantiateTools(inspections);
    enableInspections(tools.toArray(new InspectionProfileEntry[0]));
  }

  @Override
  public void disableInspections(InspectionProfileEntry @NotNull ... inspections) {
    InspectionsKt.disableInspections(getProject(), inspections);
  }

  @Override
  public void enableInspections(InspectionToolProvider @NotNull ... providers) {
    List<Class<? extends LocalInspectionTool>> classes = Stream.of(providers)
      .flatMap(p -> Stream.of(p.getInspectionClasses()))
      .filter(LocalInspectionTool.class::isAssignableFrom)
      .collect(Collectors.toList());
    enableInspections(classes);
  }

  @Override
  public long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, String @NotNull ... filePaths) {
    if (filePaths.length > 0) {
      configureByFilesInner(filePaths);
    }
    return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings);
  }

  @Override
  public long testHighlightingAllFiles(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, String @NotNull ... paths) {
    return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings, Stream.of(paths).map(this::copyFileToProject));
  }

  @Override
  public long testHighlightingAllFiles(boolean checkWarnings,
                                       boolean checkInfos,
                                       boolean checkWeakWarnings,
                                       VirtualFile @NotNull ... files) {
    return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings, Stream.of(files));
  }

  private long collectAndCheckHighlighting(boolean checkWarnings,
                                           boolean checkInfos,
                                           boolean checkWeakWarnings,
                                           @NotNull Stream<? extends VirtualFile> files) {
    record FileHighlighting(PsiFile file, Editor editor, ExpectedHighlightingData data) {}
    List<FileHighlighting> data = files.map(file -> {
      PsiFile psiFile = myPsiManager.findFile(file);
      assertNotNull(psiFile);
      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      assertNotNull(document);
      ExpectedHighlightingData datum =
        new ExpectedHighlightingData(document, checkWarnings, checkWeakWarnings, checkInfos, false);
      datum.init();
      return new FileHighlighting(psiFile, createEditor(file), datum);
    }).toList();
    long elapsed = 0;
    for (FileHighlighting highlighting : data) {
      setFileAndEditor(highlighting.file().getVirtualFile(), highlighting.editor());
      elapsed += collectAndCheckHighlighting(highlighting.data());
    }
    return elapsed;
  }

  @Override
  public long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {
    return checkHighlighting(checkWarnings, checkInfos, checkWeakWarnings, false);
  }

  @Override
  public long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, boolean ignoreExtraHighlighting) {
    return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings, ignoreExtraHighlighting);
  }

  @Override
  public long checkHighlighting() {
    return checkHighlighting(true, false, true);
  }

  @Override
  public long testHighlighting(String @NotNull ... filePaths) {
    return testHighlighting(true, false, true, filePaths);
  }

  @Override
  public long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @NotNull VirtualFile file) {
    openFileInEditor(file);
    return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings);
  }

  @NotNull
  @Override
  public HighlightTestInfo testFile(String @NotNull ... filePath) {
    return new HighlightTestInfo(myProjectFixture.getTestRootDisposable(), filePath) {
      @Override
      public HighlightTestInfo doTest() {
        configureByFiles(filePaths);
        ExpectedHighlightingData data =
          new ExpectedHighlightingData(editor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, false);
        if (checkSymbolNames) data.checkSymbolNames();
        data.init();
        collectAndCheckHighlighting(data);
        return this;
      }
    };
  }

  @Override
  public void openFileInEditor(@NotNull VirtualFile file) {
    setFileAndEditor(file, createEditor(file));
  }

  @Override
  public void testInspection(@NotNull String testDir, @NotNull InspectionToolWrapper<?,?> toolWrapper) {
    VirtualFile sourceDir = copyDirectoryToProject(new File(testDir, "src").getPath(), "");
    testInspection(testDir, toolWrapper, sourceDir);
  }

  @Override
  public void testInspection(@NotNull String testDir, @NotNull InspectionToolWrapper<?, ?> toolWrapper, @NotNull VirtualFile sourceDir) {
    PsiDirectory psiDirectory = getPsiManager().findDirectory(sourceDir);
    assertNotNull(psiDirectory);

    AnalysisScope scope = new AnalysisScope(psiDirectory);
    scope.invalidate();

    GlobalInspectionContextForTests globalContext =
      InspectionsKt.createGlobalContextForTool(scope, getProject(), Collections.<InspectionToolWrapper<?, ?>>singletonList(toolWrapper));

    InspectionTestUtil.runTool(toolWrapper, scope, globalContext);
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, new File(getTestDataPath(), testDir).getPath());
  }

  @Override
  public @NotNull PsiSymbolReference findSingleReferenceAtCaret() {
    PsiFile file = getFile();
    assertNotNull(file);
    return assertOneElement(ReferencesKt.referencesAt(file, getCaretOffset()));
  }

  @Override
  @Nullable
  public PsiReference getReferenceAtCaretPosition(String @NotNull ... filePaths) {
    if (filePaths.length > 0) {
      configureByFilesInner(filePaths);
    }
    return ReadAction.compute(() -> getFile().findReferenceAt(editor.getCaretModel().getOffset()));
  }

  @Override
  @NotNull
  public PsiReference getReferenceAtCaretPositionWithAssertion(String @NotNull ... filePaths) {
    PsiReference reference = getReferenceAtCaretPosition(filePaths);
    assertNotNull("no reference found at " + editor.getCaretModel().getLogicalPosition(), reference);
    return reference;
  }

  @Override
  @NotNull
  public List<IntentionAction> getAvailableIntentions(String @NotNull ... filePaths) {
    if (filePaths.length > 0) {
      configureByFilesInner(filePaths);
    }
    return getAvailableIntentions();
  }

  @Override
  @NotNull
  public List<IntentionAction> getAllQuickFixes(String @NotNull ... filePaths) {
    if (filePaths.length != 0) {
      configureByFilesInner(filePaths);
    }
    return myEditorTestFixture.getAllQuickFixes();
  }

  @Override
  @NotNull
  public List<IntentionAction> getAvailableIntentions() {
    doHighlighting();
    return getAvailableIntentions(ReadAction.compute(() -> getHostEditor()), ReadAction.compute(() -> getHostFileAtCaret()));
  }

  @NotNull
  private Editor getHostEditor() {
    return InjectedLanguageEditorUtil.getTopLevelEditor(getEditor());
  }

  @NotNull
  private PsiFile getHostFileAtCaret() {
    return Objects.requireNonNull(PsiUtilBase.getPsiFileInEditor(getHostEditor(), getProject()));
  }

  @NotNull
  @Override
  public List<IntentionAction> filterAvailableIntentions(@NotNull String hint) {
    return ContainerUtil.filter(getAvailableIntentions(), action -> action.getText().startsWith(hint));
  }

  @NotNull
  @Override
  public IntentionAction findSingleIntention(@NotNull String hint) {
    List<IntentionAction> list = filterAvailableIntentions(hint);
    if (list.isEmpty()) {
      fail("\"" + hint + "\" not in [" + StringUtil.join(getAvailableIntentions(), INTENTION_NAME_FUN, ", ") + "]");
    }
    else if (list.size() > 1) {
      fail("Too many intentions found for \"" + hint + "\": [" + StringUtil.join(list, INTENTION_NAME_FUN, ", ") + "]");
    }
    return assertOneElement(list);
  }

  @Override
  public IntentionAction getAvailableIntention(@NotNull String intentionName, String @NotNull ... filePaths) {
    List<IntentionAction> intentions = getAvailableIntentions(filePaths);
    IntentionAction action = CodeInsightTestUtil.findIntentionByText(intentions, intentionName);
    if (action == null) {
      LOG.debug(intentionName + " not found among " + StringUtil.join(intentions, IntentionAction::getText, ","));
    }
    return action;
  }

  @Override
  public void checkPreviewAndLaunchAction(@NotNull IntentionAction action) {
    if (skipPreview(action)) {
      launchAction(action);
    } else {
      String text = getIntentionPreviewText(action);
      assertNotNull(action.getText(), text);
      launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      assertEquals(action.getText(), InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(getFile()).getText(), text);
    }
  }

  private static boolean skipPreview(@NotNull IntentionAction action) {
    return IntentionActionDelegate.unwrap(action) instanceof CleanupInspectionIntention;
  }

  @Override
  public @Nullable String getIntentionPreviewText(@NotNull IntentionAction action) {
    // Run in background thread to catch accidental write-actions during preview generation
    try {
      return ReadAction.nonBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor()))
        .submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @Nullable String getIntentionPreviewText(@NotNull String hint) {
    IntentionActionWithTextCaching action = findCachingAction(hint);
    if (action == null) return null;
    IntentionPreviewInfo info =
      IntentionPreviewPopupUpdateProcessor.getPreviewInfo(getProject(), action.getAction(), getFile(), getEditor(), action.getFixOffset());
    return info == IntentionPreviewInfo.EMPTY ? null : ((IntentionPreviewDiffResult)info).getNewText();
  }

  @Override
  public void launchAction(@NotNull String hint) {
    IntentionActionWithTextCaching action = findCachingAction(hint);
    if (action == null) throw new IllegalArgumentException();
    ShowIntentionActionsHandler.chooseActionAndInvoke(getHostFile(), getHostEditor(), action.getAction(), action.getText(), action.getFixOffset());
  }

  @Nullable
  private IntentionActionWithTextCaching findCachingAction(@NotNull String hint) {
    doHighlighting();
    List<IntentionActionWithTextCaching> list = getIntentionListStep(getEditor(), getFile()).getValues();
    return ContainerUtil.find(list, caching -> caching.getAction().getText().startsWith(hint));
  }

  @Override
  public void checkIntentionPreviewHtml(@NotNull IntentionAction action, @NotNull @Language("HTML") String expected) {
    // Run in background thread to catch accidental write-actions during preview generation
    IntentionPreviewInfo info;
    try {
      info = ReadAction.nonBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewInfo(getProject(), action, getFile(), getEditor()))
        .submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    assertTrue(action.getText(), info instanceof IntentionPreviewInfo.Html);
    assertEquals(action.getText(), expected, ((IntentionPreviewInfo.Html)info).content().toString());
  }

  @Override
  public void launchAction(@NotNull IntentionAction action) {
    EdtTestUtil.runInEdtAndWait(() -> invokeIntention(action, getHostFileAtCaret(), getHostEditor()));
  }

  @Override
  public void testCompletion(String @NotNull [] filesBefore, @NotNull @TestDataFile String fileAfter) {
    testCompletionTyping(filesBefore, "", fileAfter);
  }

  @Override
  public void testCompletionTyping(String @NotNull [] filesBefore, @NotNull String toType, @NotNull String fileAfter) {
    assertInitialized();
    configureByFiles(filesBefore);
    complete(CompletionType.BASIC);
    type(toType);
    try {
      checkResultByFile(fileAfter);
    }
    catch (RuntimeException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("LookupElementStrings = " + getLookupElementStrings());
      throw e;
    }
  }

  protected void assertInitialized() {
    assertNotNull("setUp() hasn't been called", myPsiManager);
  }

  @Override
  public void testCompletion(@NotNull String fileBefore,
                             @NotNull String fileAfter,
                             @TestDataFile String @NotNull ... additionalFiles) {
    testCompletionTyping(fileBefore, "", fileAfter, additionalFiles);
  }

  @Override
  public void testCompletionTyping(@NotNull @TestDataFile String fileBefore,
                                   @NotNull String toType,
                                   @NotNull @TestDataFile String fileAfter,
                                   @TestDataFile String @NotNull ... additionalFiles) {
    testCompletionTyping(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)), toType, fileAfter);
  }

  @Override
  public void testCompletionVariants(@NotNull String fileBefore, String @NotNull ... expectedItems) {
    assertInitialized();
    List<String> result = getCompletionVariants(fileBefore);
    assertNotNull(result);
    UsefulTestCase.assertSameElements(result, expectedItems);
  }

  @Override
  public List<String> getCompletionVariants(String @NotNull ... filesBefore) {
    assertInitialized();
    configureByFiles(filesBefore);
    LookupElement[] items = complete(CompletionType.BASIC);
    assertNotNull("No lookup was shown, probably there was only one lookup element that was inserted automatically", items);
    return getLookupElementStrings();
  }

  @Override
  @Nullable
  public List<String> getLookupElementStrings() {
    assertInitialized();
    return myEditorTestFixture.getLookupElementStrings();
  }

  @Override
  public void finishLookup(char completionChar) {
    myEditorTestFixture.finishLookup(completionChar);
  }

  @Override
  public void testRename(@NotNull String fileBefore,
                         @NotNull String fileAfter,
                         @NotNull String newName,
                         @TestDataFile String @NotNull ... additionalFiles) {
    assertInitialized();
    configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)));
    testRename(fileAfter, newName);
  }

  @Override
  public void testRenameUsingHandler(@NotNull String fileBefore,
                                     @NotNull String fileAfter,
                                     @NotNull String newName,
                                     String @NotNull ... additionalFiles) {
    assertInitialized();
    configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)));
    testRenameUsingHandler(fileAfter, newName);
  }

  @Override
  public void testRenameUsingHandler(@NotNull String fileAfter, @NotNull String newName) {
    renameElementAtCaretUsingHandler(newName);
    checkResultByFile(fileAfter);
  }

  @Override
  public void testRename(@NotNull String fileAfter, @NotNull String newName) {
    renameElementAtCaret(newName);
    checkResultByFile(fileAfter);
  }

  @Override
  @NotNull
  public PsiElement getElementAtCaret() {
    assertInitialized();
    return myEditorTestFixture.getElementAtCaret();
  }

  @Override
  public void renameElementAtCaret(@NotNull String newName) {
    renameElement(getElementAtCaret(), newName);
  }

  @Override
  public void renameElementAtCaretUsingHandler(@NotNull String newName) {
    DataContext editorContext = EditorUtil.getEditorDataContext(editor);
    DataContext context = CustomizedDataContext.withSnapshot(editorContext, sink -> {
      sink.set(PsiElementRenameHandler.DEFAULT_NAME, newName);
    });
    RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
    assertNotNull("No handler for this context", renameHandler);

    renameHandler.invoke(getProject(), editor, getFile(), context);
  }

  @Override
  public void renameElement(@NotNull PsiElement element, @NotNull String newName) {
    final boolean searchInComments = false;
    final boolean searchTextOccurrences = false;
    renameElement(element, newName, searchInComments, searchTextOccurrences);
  }

  @Override
  public void renameElement(@NotNull PsiElement element,
                            @NotNull String newName,
                            boolean searchInComments,
                            boolean searchTextOccurrences) {
    PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, editor);
    if (substitution == null) return;
    new RenameProcessor(getProject(), substitution, newName, searchInComments, searchTextOccurrences).run();
  }

  @Override
  public void renameTarget(@NotNull RenameTarget renameTarget, @NotNull String newName) {
    RenameKt.renameAndWait(getProject(), renameTarget, newName);
  }

  @Override
  public <T extends PsiElement> T findElementByText(@NotNull String text, @NotNull Class<T> elementClass) {
    return myEditorTestFixture.findElementByText(text, elementClass);
  }

  @Override
  public void type(char c) {
    assertInitialized();
    myEditorTestFixture.type(c);
  }

  @Override
  public void type(@NotNull String s) {
    myEditorTestFixture.type(s);
  }

  @Override
  public void performEditorAction(@NotNull String actionId, @Nullable AnActionEvent actionEvent) {
    assertInitialized();
    EdtTestUtil.runInEdtAndWait(() -> myEditorTestFixture.performEditorAction(actionId, actionEvent));
  }

  @NotNull
  @Override
  public Presentation testAction(@NotNull AnAction action) {
    AnActionEvent e = TestActionEvent.createTestEvent(action);
    ActionUtil.performDumbAwareUpdate(action, e, false);
    if (e.getPresentation().isEnabled()) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, e);
    }
    return e.getPresentation();
  }

  @NotNull
  @Override
  public Collection<UsageInfo> testFindUsages(String @NotNull ... fileNames) {
    assertInitialized();
    if (fileNames.length > 0) {
      configureByFiles(fileNames);
    }
    int flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED;
    PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), flags);
    assertNotNull("Cannot find referenced element", targetElement);
    return findUsages(targetElement);
  }

  @NotNull
  @Override
  public Collection<Usage> testFindUsagesUsingAction(String @NotNull ... fileNames) {
    assertInitialized();
    if (fileNames.length > 0) { // don't change configured files if already configured
      configureByFiles(fileNames);
    }
    EdtTestUtil.runInEdtAndWait(() -> {
      myEditorTestFixture.performEditorAction(IdeActions.ACTION_FIND_USAGES);
      FindUsagesManager.waitForAsyncTaskCompletion(getProject());
    });
    Disposer.register(getTestRootDisposable(), () -> {
      UsageViewContentManager usageViewManager = UsageViewContentManager.getInstance(getProject());
      Content selectedContent;
      while ((selectedContent = usageViewManager.getSelectedContent()) != null) {
        usageViewManager.closeContent(selectedContent);
      }
    });
    return EdtTestUtil.runInEdtAndGet(() -> {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      long startMillis = System.currentTimeMillis();
      UsageView view;
      boolean viewWasInitialized = false;
      while ((view = UsageViewManager.getInstance(getProject()).getSelectedUsageView()) == null || view.isSearchInProgress()) {
        IdeEventQueue.getInstance().flushQueue();
        viewWasInitialized |= view != null;
        if (!viewWasInitialized && System.currentTimeMillis() - startMillis > TimeUnit.SECONDS.toMillis(10)) {
          fail("UsageView wasn't shown");
          return Collections.emptyList();
        }
      }
      return view.getUsages();
    });
  }

  @NotNull
  @Override
  public Collection<UsageInfo> findUsages(@NotNull PsiElement targetElement) {
    return findUsages(targetElement, null);
  }

  @NotNull
  @Override
  public String getUsageViewTreeTextRepresentation(@NotNull Collection<? extends UsageInfo> usages) {
    UsageViewImpl usageView = (UsageViewImpl)UsageViewManager
      .getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY,
                                                 ContainerUtil.map(usages, usage -> new UsageInfo2UsageAdapter(usage)).toArray(Usage.EMPTY_ARRAY),
                                                 new UsageViewPresentation(),
                                                 null);
    return getUsageViewTreeTextRepresentation(usageView);
  }

  @Override
  public @NotNull String getUsageViewTreeTextRepresentation(@NotNull List<UsageTarget> usageTargets,
                                                            @NotNull Collection<? extends Usage> usages) {
    UsageViewImpl usageView = (UsageViewImpl)UsageViewManager.getInstance(getProject())
      .createUsageView(usageTargets.toArray(UsageTarget.EMPTY_ARRAY), usages.toArray(Usage.EMPTY_ARRAY), new UsageViewPresentation(), null);

    return getUsageViewTreeTextRepresentation(usageView);
  }

  @NotNull
  @Override
  public String getUsageViewTreeTextRepresentation(@NotNull PsiElement targetElement) {
    FindUsagesManager usagesManager = ((FindManagerImpl)FindManager.getInstance(getProject())).getFindUsagesManager();
    FindUsagesHandler handler = usagesManager.getFindUsagesHandler(targetElement, false);
    assertNotNull("Cannot find handler for: " + targetElement, handler);
    UsageViewImpl usageView = (UsageViewImpl)usagesManager.doFindUsages(handler.getPrimaryElements(),
                                                                              handler.getSecondaryElements(),
                                                                              handler,
                                                                              handler.getFindUsagesOptions(),
                                                                              false);
    return getUsageViewTreeTextRepresentation(usageView);

  }

  @Override
  public @NotNull String getUsageViewTreeTextRepresentation(@NotNull SearchTarget target) {
    Project project = getProject();

    SearchScope searchScope = coalesce(target.getMaximalSearchScope(), GlobalSearchScope.allScope(project));
    AllSearchOptions allOptions = new AllSearchOptions(UsageOptions.createOptions(searchScope), true);
    List<UsageTarget> usageTargets = List.of(new SearchTarget2UsageTarget(project, target, allOptions));
    Collection<? extends Usage> usages = buildUsageViewQuery(getProject(), target, allOptions).findAll();

    return getUsageViewTreeTextRepresentation(usageTargets, usages);
  }

  @NotNull
  public Collection<UsageInfo> findUsages(@NotNull PsiElement targetElement, @Nullable SearchScope scope) {
    Project project = getProject();
    FindUsagesHandler handler =
      ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager().getFindUsagesHandler(targetElement, false);

    CommonProcessors.CollectProcessor<UsageInfo> processor =
      new CommonProcessors.CollectProcessor<>(Collections.synchronizedList(new ArrayList<>()));
    assertNotNull("Cannot find handler for: " + targetElement, handler);
    PsiElement[] psiElements = ArrayUtil.mergeArrays(handler.getPrimaryElements(), handler.getSecondaryElements());
    FindUsagesOptions options = handler.getFindUsagesOptions(null);
    if (scope != null) options.searchScope = scope;
    for (PsiElement psiElement : psiElements) {
      handler.processElementUsages(psiElement, processor, options);
    }
    return processor.getResults();
  }

  @Override
  public RangeHighlighter @NotNull [] testHighlightUsages(String @NotNull ... files) {
    configureByFiles(files);
    testAction(new HighlightUsagesAction());
    Editor editor = getEditor();
    return editor.getMarkupModel().getAllHighlighters();
  }

  @Override
  public void moveFile(@NotNull String filePath, @NotNull String to, @TestDataFile String @NotNull ... additionalFiles) {
    assertInitialized();
    Project project = getProject();
    configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, filePath)));
    VirtualFile file = findFileInTempDir(to);
    assertNotNull("Directory " + to + " not found", file);
    assertTrue(to + " is not a directory", file.isDirectory());
    PsiDirectory directory = myPsiManager.findDirectory(file);
    new MoveFilesOrDirectoriesProcessor(project, new PsiElement[]{getFile()}, directory, false, false, null, null).run();
  }

  @Override
  @Nullable
  public GutterMark findGutter(@NotNull String filePath) {
    configureByFilesInner(filePath);
    CommonProcessors.FindFirstProcessor<GutterMark> processor = new CommonProcessors.FindFirstProcessor<>();
    doHighlighting();
    processGuttersAtCaret(editor, getProject(), processor);
    return processor.getFoundValue();
  }

  @NotNull
  @Override
  public List<GutterMark> findGuttersAtCaret() {
    CommonProcessors.CollectProcessor<GutterMark> processor = new CommonProcessors.CollectProcessor<>();
    doHighlighting();
    processGuttersAtCaret(editor, getProject(), processor);
    return new ArrayList<>(processor.getResults());
  }
  @NotNull
  public List<GutterMark> findGuttersAtCaret(@NotNull String filePath) {
    configureByFilesInner(filePath);
    return findGuttersAtCaret();
  }

  public static boolean processGuttersAtCaret(Editor editor, Project project, @NotNull Processor<? super GutterMark> processor) {
    int offset = editor.getCaretModel().getOffset();

    RangeHighlighter[] highlighters = DocumentMarkupModel.forDocument(editor.getDocument(), project, true).getAllHighlighters();
    for (RangeHighlighter highlighter : highlighters) {
      GutterMark renderer = highlighter.getGutterIconRenderer();
      if (renderer != null &&
          editor.getDocument().getLineNumber(offset) == editor.getDocument().getLineNumber(highlighter.getStartOffset()) &&
          !processor.process(renderer)) {
        return false;
      }
    }
    return true;
  }

  @Override
  @NotNull
  public List<GutterMark> findAllGutters(@NotNull String filePath) {
    configureByFilesInner(filePath);
    return findAllGutters();
  }

  @Override
  @NotNull
  public List<GutterMark> findAllGutters() {
    Project project = getProject();
    SortedMap<Integer, List<GutterMark>> result = new TreeMap<>();

    List<HighlightInfo> infos = doHighlighting();
    for (HighlightInfo info : infos) {
      addGutterIconRenderer(info.getGutterIconRenderer(), info.startOffset, result);
    }

    RangeHighlighter[] highlighters = DocumentMarkupModel.forDocument(editor.getDocument(), project, true).getAllHighlighters();
    for (RangeHighlighter highlighter : highlighters) {
      if (!highlighter.isValid()) continue;
      addGutterIconRenderer(highlighter.getGutterIconRenderer(), highlighter.getStartOffset(), result);
    }
    return ContainerUtil.concat(result.values());
  }

  @Override
  public PsiFile addFileToProject(@NotNull String relativePath, @NotNull String fileText) {
    assertInitialized();
    return addFileToProject(getTempDirPath(), relativePath, fileText);
  }

  protected PsiFile addFileToProject(@NotNull String rootPath, @NotNull String relativePath, @NotNull String fileText) {
    try {
      VirtualFile file = WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<VirtualFile, IOException>)() -> {
        try {
          VirtualFile f;
          if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
            f = myTempDirFixture.createFile(relativePath, fileText);
          }
          else if (myProjectFixture instanceof HeavyIdeaTestFixture){
            f = ((HeavyIdeaTestFixture)myProjectFixture).addFileToProject(rootPath, relativePath, fileText).getViewProvider()
                                                           .getVirtualFile();
          }
          else {
            f = myTempDirFixture.createFile(relativePath, fileText);
          }

          prepareVirtualFile(f);

          return f;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        finally {
          PsiManager.getInstance(getProject()).dropPsiCaches();
          IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        }
      });
      return ReadAction.compute(() -> PsiManager.getInstance(getProject()).findFile(file));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> void registerExtension(@NotNull ExtensionsArea area, @NotNull ExtensionPointName<T> epName, @NotNull T extension) {
    assertInitialized();
    area.getExtensionPoint(epName).registerExtension(extension, myProjectFixture.getTestRootDisposable());
  }

  @NotNull
  @Override
  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  @Override
  public LookupElement[] complete(@NotNull CompletionType type) {
    return myEditorTestFixture.complete(type);
  }

  @Override
  public LookupElement[] complete(@NotNull CompletionType type, int invocationCount) {
    assertInitialized();
    return myEditorTestFixture.complete(type, invocationCount);
  }

  @Override
  public LookupElement @Nullable [] completeBasic() {
    return myEditorTestFixture.completeBasic();
  }

  @Override
  @NotNull
  public final List<LookupElement> completeBasicAllCarets(@Nullable Character charToTypeAfterCompletion) {
    return myEditorTestFixture.completeBasicAllCarets(charToTypeAfterCompletion);
  }

  @Override
  public void saveText(@NotNull VirtualFile file, @NotNull String text) {
    try {
    WriteAction.runAndWait(() -> VfsUtil.saveText(file, text));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public LookupElement @Nullable [] getLookupElements() {
    return myEditorTestFixture.getLookupElements();
  }

  @Override
  public void checkResult(@NotNull String expectedText) {
    checkResult(expectedText, false);
  }

  @Override
  public void checkResult(@NotNull String expectedText, boolean stripTrailingSpaces) {
    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    if (policy != null) {
      policy.beforeCheckResult(getFile());
    }
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      EditorUtil.fillVirtualSpaceUntilCaret(getHostEditor());
      checkResult("TEXT", stripTrailingSpaces, SelectionAndCaretMarkupLoader.fromText(expectedText), getHostFile().getText());
    });
  }

  @Override
  public void checkResult(@NotNull String filePath, @NotNull String expectedText, boolean stripTrailingSpaces) {
    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    if (policy != null) {
      policy.beforeCheckResult(getFile());
    }
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      PsiFile psiFile = getFileToCheck(filePath);
      checkResult("TEXT", stripTrailingSpaces, SelectionAndCaretMarkupLoader.fromText(expectedText), psiFile.getText());
    });
  }

  @Override
  public void checkResultByFile(@NotNull String expectedFile) {
    checkResultByFile(expectedFile, false);
  }

  @Override
  public void checkResultByFile(@NotNull String expectedFile, boolean ignoreTrailingWhitespaces) {
    assertInitialized();
    ApplicationManager.getApplication().invokeAndWait(() -> checkResultByFile(expectedFile, getHostFile(), ignoreTrailingWhitespaces));
  }

  @Override
  public void checkResultByFile(@NotNull String filePath, @NotNull String expectedFile, boolean ignoreTrailingWhitespaces) {
    assertInitialized();
    ApplicationManager.getApplication().invokeAndWait(() -> checkResultByFile(expectedFile, getFileToCheck(filePath), ignoreTrailingWhitespaces));
  }

  private PsiFile getFileToCheck(String filePath) {
    String path = filePath.replace(File.separatorChar, '/');
    VirtualFile copy = findFileInTempDir(path);
    assertNotNull("could not find results file " + path, copy);
    PsiFile psiFile = myPsiManager.findFile(copy);
    assertNotNull(copy.getPath(), psiFile);
    return psiFile;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    EdtTestUtil.runInEdtAndWait(() -> {
      myProjectFixture.setUp();
      myTempDirFixture.setUp();

      VirtualFile tempDir = myTempDirFixture.getFile("");
      assertNotNull(tempDir);
      HeavyPlatformTestCase.synchronizeTempDirVfs(tempDir);

      myPsiManager = (PsiManagerImpl)PsiManager.getInstance(getProject());
      InspectionsKt.configureInspections(LocalInspectionTool.EMPTY_ARRAY, getProject(), myProjectFixture.getTestRootDisposable());

      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
      daemonCodeAnalyzer.prepareForTest();

      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);
      ensureIndexesUpToDate(getProject());
      CodeStyle.setTemporarySettings(getProject(), CodeStyle.createTestSettings());

      IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
      if (policy != null) {
        policy.setUp(getProject(), getTestRootDisposable(), getTestDataPath());
      }
      ActionUtil.performActionDumbAwareWithCallbacks(
        new EmptyAction(true), AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT));
    });

    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      ModuleRootManager.getInstance(module).orderEntries().getAllLibrariesAndSdkClassesRoots(); // instantiate all VFPs
    }
    if (shouldTrackVirtualFilePointers()) {
      myVirtualFilePointerTracker = new VirtualFilePointerTracker();
    }
    myLibraryTableTracker = new LibraryTableTracker();

    IndexingTestUtil.waitUntilIndexesAreReady((getProject()));
  }

  protected boolean shouldTrackVirtualFilePointers() {
    return true;
  }

  @Override
  public void tearDown() throws Exception {
    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    AtomicReference<ProjectRootManagerComponent> projectRootManagerComponentRef = new AtomicReference<>();
    runAll(
      () -> EdtTestUtil.runInEdtAndWait(() -> {
        if (ApplicationManager.getApplication() == null) {
          return;
        }

        Project project;
        try {
          project = myProjectFixture.getProject();
        }
        catch (AssertionError | UninitializedPropertyAccessException ignore) {
          project = null;
        }

        if (project != null) {
          CodeStyle.dropTemporarySettings(project);
          // clear "show param info" delayed requests leaking project
          AutoPopupController autoPopupController = project.getServiceIfCreated(AutoPopupController.class);
          if (autoPopupController != null) {
            autoPopupController.cancelAllRequests();
          }
        }

        // return default value to avoid unnecessary save
        DaemonCodeAnalyzerSettings daemonCodeAnalyzerSettings =
          ApplicationManager.getApplication().getServiceIfCreated(DaemonCodeAnalyzerSettings.class);
        if (daemonCodeAnalyzerSettings != null) {
          daemonCodeAnalyzerSettings.setImportHintEnabled(true);
        }

        if (project != null) {
          closeOpenFiles();
          ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project)).cleanupAfterTest();
          // needed for myVirtualFilePointerTracker check below
          ((ProjectRootManagerImpl)ProjectRootManager.getInstance(project)).clearScopesCachesForModules();
          projectRootManagerComponentRef.set(ObjectUtils.tryCast(ProjectRootManager.getInstance(project), ProjectRootManagerComponent.class));
        }
      }),
      () -> {
        clearFileAndEditor();
        myPsiManager = null;
      },
      () -> disposeRootDisposable(),
      () -> EdtTestUtil.runInEdtAndWait(() -> myProjectFixture.tearDown()),
      () -> EdtTestUtil.runInEdtAndWait(() -> myTempDirFixture.tearDown()),
      () -> super.tearDown(),
      () -> {
        // Even ProjectRootManagerComponent is a project service implementing Disposable, it might be not disposed in test light projects.
        // Virtual file pointers could be created too late - during `myProjectFixture.tearDown()` - on
        // `fixture.getTestRootDisposable()` disposing or on project files cleanup.
        // Need to dispose virtual file pointers after that moment.
        // It wouldn't be needed if `ProjectRootManagerComponent.dispose()` would get called in tests, like in production.
        ProjectRootManagerComponent projectRootManagerComponent = projectRootManagerComponentRef.getAndSet(null);
        if (projectRootManagerComponent != null) {
          projectRootManagerComponent.disposeVirtualFilePointersAfterTest();
        }
        if (myVirtualFilePointerTracker != null) {
          myVirtualFilePointerTracker.assertPointersAreDisposed();
        }
      },
      () -> {
        if (myLibraryTableTracker != null) {
          myLibraryTableTracker.assertDisposed();
        }
      }
    );
  }

  private void closeOpenFiles() {
    Project project = getProject();
    if (project == null) {
      return;
    }

    LookupManager.hideActiveLookup(project);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FileEditorManagerEx.getInstanceEx(project).closeAllFiles();
    EditorHistoryManager.getInstance(project).removeAllFiles();
  }

  private PsiFile @NotNull [] configureByFilesInner(String @NotNull ... filePaths) {
    assertInitialized();
    clearFileAndEditor();
    myAllPsiFiles = new PsiFile[filePaths.length];
    for (int i = filePaths.length - 1; i >= 0; i--) {
      myAllPsiFiles[i] = configureByFileInner(filePaths[i]);
    }
    return myAllPsiFiles;
  }

  @Override
  public PsiFile configureByFile(@NotNull String file) {
    configureByFilesInner(file);
    return getFile();
  }

  @Override
  public PsiFile @NotNull [] configureByFiles(String @NotNull ... files) {
    return configureByFilesInner(files);
  }

  @Override
  public PsiFile configureByText(@NotNull FileType fileType, @NotNull String text) {
    assertInitialized();
    String extension = fileType.getDefaultExtension();
    associateExtensionTemporarily(fileType, extension, getTestRootDisposable());
    String fileName = "aaa." + extension;
    return configureByText(fileName, text);
  }

  public static void associateExtensionTemporarily(@NotNull FileType fileType,
                                                   @NotNull String extension,
                                                   @NotNull Disposable parentDisposable) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (!fileType.equals(fileTypeManager.getFileTypeByExtension(extension))) {
      WriteAction.runAndWait(() -> {
        fileTypeManager.associateExtension(fileType, extension);
        IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects();
      });
      Disposer.register(parentDisposable, () -> {
        WriteAction.runAndWait(() -> {
          fileTypeManager.removeAssociatedExtension(fileType, extension);
          IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects();
        });
      });
    }
  }

  @Override
  public PsiFile configureByText(@NotNull String fileName, @NotNull String text) {
    assertInitialized();
    VirtualFile vFile = createFile(fileName, text);
    return configureInner(vFile, SelectionAndCaretMarkupLoader.fromFile(vFile));
  }

  @Override
  public VirtualFile createFile(@NotNull String fileName, @NotNull String text) {
    assertInitialized();
    try {
      VirtualFile createdFile = WriteCommandAction.writeCommandAction(getProject()).compute(() -> {
        VirtualFile file;
        if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
          VirtualFile root = LightPlatformTestCase.getSourceRoot();
          root.refresh(false, false);
          file = root.findOrCreateChildData(this, fileName);
          assertNotNull(fileName + " not found in " + root.getPath(), file);
        }
        else if (myTempDirFixture instanceof TempDirTestFixtureImpl) {
          Path tempFile = ((TempDirTestFixtureImpl)myTempDirFixture).createTempFile(fileName);
          file = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(tempFile.toString()));
          assertNotNull(tempFile + " not found", file);
        }
        else {
          file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(getTempDirPath(), fileName));
          assertNotNull(fileName + " not found in " + getTempDirPath(), file);
        }

        prepareVirtualFile(file);

        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document != null) {
          PsiDocumentManager.getInstance(getProject()).doPostponedOperationsAndUnblockDocument(document);
          FileDocumentManager.getInstance().saveDocument(document);
        }

        VfsUtil.saveText(file, text);
        return file;
      });

      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
      return createdFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Document getDocument(@NotNull PsiFile file) {
    assertInitialized();
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  private PsiFile configureByFileInner(@NotNull String filePath) {
    assertInitialized();
    VirtualFile file = copyFileToProject(filePath);
    return configureByFileInner(file);
  }

  @Override
  public PsiFile configureFromTempProjectFile(@NotNull String filePath) {
    VirtualFile fileInTempDir = findFileInTempDir(filePath);
    if (fileInTempDir == null) {
      throw new IllegalArgumentException("Could not find file in temp dir: " + filePath);
    }
    return configureByFileInner(fileInTempDir);
  }

  @Override
  public void configureFromExistingVirtualFile(@NotNull VirtualFile virtualFile) {
    configureByFileInner(virtualFile);
  }

  private PsiFile configureByFileInner(@NotNull VirtualFile copy) {
    return configureInner(copy, SelectionAndCaretMarkupLoader.fromFile(copy));
  }

  private PsiFile configureInner(@NotNull VirtualFile copy, @NotNull SelectionAndCaretMarkupLoader loader) {
    assertInitialized();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (!copy.getFileType().isBinary()) {
        switch (mySelectionAndCaretMarkupApplyPolicy) {
          case UPDATE_FILE_AND_KEEP_DOCUMENT_CLEAN -> {
            try {
              ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Void, IOException>)() -> {
                copy.setBinaryContent(loader.newFileText.getBytes(copy.getCharset()));
                return null;
              });
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
          case UPDATE_DOCUMENT_AND_LEAVE_IT_DIRTY -> {
            Document document = FileDocumentManager.getInstance().getDocument(copy);
            if (document == null) {
              throw new IllegalStateException("Document not found: " + copy);
            }
            WriteAction.runAndWait(() -> document.setText(loader.newFileText));
          }
          default -> throw new IllegalArgumentException(String.valueOf(mySelectionAndCaretMarkupApplyPolicy));
        }
      }

      setFileAndEditor(copy, createEditor(copy));
      if (editor == null) {
        fail("editor couldn't be created for: " + copy.getPath() + ", use copyFileToProject() instead of configureByFile()");
      }

      EditorTestUtil.setCaretsAndSelection(editor, loader.caretState);

      Module module = getModule();
      if (module != null) {
        FacetManager facetManager = FacetManager.getInstance(module);
        for (Facet<?> facet : facetManager.getAllFacets()) {
          facetManager.facetConfigurationChanged(facet);
        }
      }
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
      if (caresAboutInjection) {
        setupEditorForInjectedLanguage();
      }

      IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
      if (policy != null) {
        policy.testFileConfigured(getFile());
      }
    });

    return getFile();
  }

  protected void prepareVirtualFile(@NotNull VirtualFile file) {
  }

  private void setupEditorForInjectedLanguage() {
    Editor editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(this.editor, getFile());
    if (editor instanceof EditorWindow) {
      setFileAndEditor(((EditorWindow)editor).getInjectedFile().getViewProvider().getVirtualFile(), editor);
    }
  }

  @Override
  public VirtualFile findFileInTempDir(@NotNull String filePath) {
    if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
      return myTempDirFixture.getFile(filePath);
    }
    String fullPath = getTempDirPath() + "/" + filePath;

    VirtualFile copy = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", copy);
    VfsTestUtil.assertFilePathEndsWithCaseSensitivePath(copy, filePath);
    return copy;
  }

  @NotNull
  protected Editor createEditor(@NotNull VirtualFile file) {
    Project project = getProject();
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, file), false);
    // to make UIUtil.hasFocus return true to make ShowAutoImportPass.showImports work
    UIUtil.markAsFocused(editor.getContentComponent(), true);
    DaemonCodeAnalyzer.getInstance(getProject()).restart();
    return editor;
  }

  private long collectAndCheckHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {
    return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings, false);
  }

  private long collectAndCheckHighlighting(boolean checkWarnings,
                                           boolean checkInfos,
                                           boolean checkWeakWarnings,
                                           boolean ignoreExtraHighlighting) {
    if (editor == null) {
      throw new IllegalStateException("Fixture is not configured. Call something like configureByFile() or configureByText()");
    }
    ExpectedHighlightingData data = new ExpectedHighlightingData(
      editor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, ignoreExtraHighlighting);
    data.init();
    return collectAndCheckHighlighting(data);
  }

  private PsiFile getHostFile() {
    VirtualFile hostVFile = myFile instanceof VirtualFileWindow ? ((VirtualFileWindow)myFile).getDelegate() : myFile;
    return ReadAction.compute(() -> PsiManager.getInstance(getProject()).findFile(hostVFile));
  }

  public long collectAndCheckHighlighting(@NotNull ExpectedHighlightingData data) {
    Project project = getProject();
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
    PsiFileImpl file = (PsiFileImpl)getHostFile();
    FileElement hardRefToFileElement = file.calcTreeElement();//to load text

    // to load AST for changed files before it's prohibited by "fileTreeAccessFilter"
    ensureIndexesUpToDate(project);

    long start = System.currentTimeMillis();
    VirtualFileFilter fileTreeAccessFilter = myVirtualFileFilter;
    Disposable disposable = Disposer.newDisposable();
    if (fileTreeAccessFilter != null) {
      PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(fileTreeAccessFilter, disposable);
    }

    //    ProfilingUtil.startCPUProfiling();
    List<HighlightInfo> infos;
    try {
      infos = doHighlighting();
      removeDuplicatedRangesForInjected(infos);
    }
    finally {
      Disposer.dispose(disposable);
    }
    //    ProfilingUtil.captureCPUSnapshot("testing");
    long elapsed = System.currentTimeMillis() - start;

    data.checkResult(file, infos, file.getText());
    if (data.hasLineMarkers()) {
      Document document = getDocument(getFile());
      data.checkLineMarkers(file, DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject()), document.getText());
    }
    Reference.reachabilityFence(hardRefToFileElement);
    return elapsed;
  }

  /**
   * Sets filter to check for which files AST loading is forbidden in {@link #collectAndCheckHighlighting(ExpectedHighlightingData)} method.
   * <p>
   * Other testing methods are not affected,
   * consider using {@link PsiManagerEx#setAssertOnFileLoadingFilter(VirtualFileFilter, Disposable)}.
   * <p>
   * Files loaded with <b>configure*</b> methods (which are called, e.g., from {@link #testHighlighting(String...)}) won't be checked
   * because their AST will be loaded before setting filter. Use {@link #copyFileToProject(String)} and similar methods.
   */
  public void setVirtualFileFilter(@Nullable VirtualFileFilter filter) {
    myVirtualFileFilter = filter;
  }

  @Override
  @NotNull
  public List<HighlightInfo> doHighlighting() {
    return myEditorTestFixture.doHighlighting(myAllowDirt, myReadEditorMarkupModel);
  }

  @NotNull
  @Override
  public List<HighlightInfo> doHighlighting(@NotNull HighlightSeverity minimalSeverity) {
    return ContainerUtil.filter(doHighlighting(), info -> info.getSeverity().compareTo(minimalSeverity) >= 0);
  }

  @NotNull
  @Override
  public String getTestDataPath() {
    return myTestDataPath;
  }

  @Override
  public void setTestDataPath(@NotNull String dataPath) {
    myTestDataPath = dataPath;
  }

  @Override
  public final Project getProject() {
    return myProjectFixture.getProject();
  }

  @Override
  public Module getModule() {
    return myProjectFixture.getModule();
  }

  @Override
  public Editor getEditor() {
    return editor;
  }

  @Override
  public int getCaretOffset() {
    return editor.getCaretModel().getOffset();
  }

  @Override
  public PsiFile getFile() {
    return myFile != null ? ReadAction.compute(() -> PsiManager.getInstance(getProject()).findFile(myFile)) : null;
  }

  @Override
  public void allowTreeAccessForFile(@NotNull VirtualFile file) {
    assert myVirtualFileFilter instanceof FileTreeAccessFilter : "configured filter does not support this method";
    ((FileTreeAccessFilter)myVirtualFileFilter).allowTreeAccessForFile(file);
  }

  @Override
  public void allowTreeAccessForAllFiles() {
    assert myVirtualFileFilter instanceof FileTreeAccessFilter : "configured filter does not support this method";
    ((FileTreeAccessFilter)myVirtualFileFilter).allowTreeAccessForAllFiles();
  }

  private void checkResultByFile(@NotNull String expectedFile, @NotNull PsiFile originalFile, boolean stripTrailingSpaces) {
    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    if (policy != null) {
      policy.beforeCheckResult(getFile());
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    if (!stripTrailingSpaces) {
      EditorUtil.fillVirtualSpaceUntilCaret(getHostEditor());
    }

    String fileText = originalFile.getText();
    String path = getTestDataPath() + "/" + expectedFile;
    String charset = Optional.ofNullable(originalFile.getVirtualFile()).map(f -> f.getCharset().name()).orElse(null);
    checkResult(expectedFile, stripTrailingSpaces, SelectionAndCaretMarkupLoader.fromFile(path, charset), fileText);
  }

  private void checkResult(@NotNull String expectedFile,
                           boolean stripTrailingSpaces,
                           @NotNull SelectionAndCaretMarkupLoader loader,
                           @NotNull String actualText) {
    assertInitialized();
    Project project = getProject();
    Editor editor = getEditor();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
    }

    UsefulTestCase.doPostponedFormatting(getProject());
    if (stripTrailingSpaces) {
      actualText = stripTrailingSpaces(actualText);
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    String expectedText = loader.newFileText;
    if (stripTrailingSpaces) {
      expectedText = stripTrailingSpaces(expectedText);
    }

    actualText = StringUtil.convertLineSeparators(actualText);

    if (!Objects.equals(expectedText, actualText)) {
      if (loader.filePath == null) {
        throw new FileComparisonFailedError(expectedFile, expectedText, actualText, null);
      }

      if (loader.caretState.hasExplicitCaret()) {
        int offset = editor.getCaretModel().getOffset();
        if (offset > -1) {
          if (offset > actualText.length()) actualText += StringUtil.repeat(" ", offset - actualText.length());
          actualText = new StringBuilder(actualText).insert(offset, "<caret>").toString();
        }
        expectedText = loader.fileText;
        if (stripTrailingSpaces) {
          expectedText = stripTrailingSpaces(expectedText);
        }
      }
      throw new FileComparisonFailedError(expectedFile, expectedText, actualText, loader.filePath);
    }

    EditorTestUtil.verifyCaretAndSelectionState(editor, loader.caretState, expectedFile, loader.filePath);
  }

  @NotNull
  private String stripTrailingSpaces(@NotNull String actualText) {
    Document document = EditorFactory.getInstance().createDocument(actualText);
    ((DocumentImpl)document).stripTrailingSpaces(getProject());
    actualText = document.getText();
    return actualText;
  }

  public void canChangeDocumentDuringHighlighting(boolean canI) {
    myAllowDirt = canI;
  }

  @NotNull
  public String getFoldingDescription(boolean withCollapseStatus) {
    Editor topEditor = getHostEditor();
    return EdtTestUtil.runInEdtAndGet(() -> {
      IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
      if (policy != null) {
        policy.waitForHighlighting(getProject(), topEditor);
      }
      EditorTestUtil.buildInitialFoldingsInBackground(topEditor);
      return getFoldingData(topEditor, withCollapseStatus);
    });
  }

  @NotNull
  public static String getFoldingData(Editor topEditor, boolean withCollapseStatus) {
    return getTagsFromSegments(topEditor.getDocument().getText(),
                               Arrays.asList(topEditor.getFoldingModel().getAllFoldRegions()),
                               FOLD,
                               foldRegion -> "text='" + foldRegion.getPlaceholderText() + "'"
                                             + (withCollapseStatus ? " expand='" + foldRegion.isExpanded() + "'" : ""));
  }

  @NotNull
  public static <T extends Segment> String getTagsFromSegments(@NotNull String text,
                                                               @NotNull Collection<? extends T> segments,
                                                               @NotNull String tagName,
                                                               @Nullable Function<? super T, String> attrCalculator) {
    List<Border> borders =
    segments.stream()
        .flatMap(region -> Stream.of(
          new Border(true, region.getStartOffset(), attrCalculator == null ? null : attrCalculator.fun(region)),
          new Border(false, region.getEndOffset(), "")))
      .sorted()
      .toList();

    StringBuilder result = new StringBuilder(text);
    for (Border border : borders) {
      StringBuilder info = new StringBuilder();
      info.append('<');
      if (border.isLeftBorder) {
        info.append(tagName);
        if (border.text != null) {
          info.append(' ').append(border.text);
        }
      }
      else {
        info.append('/').append(tagName);
      }
      info.append('>');
      result.insert(border.offset, info);
    }
    return result.toString();
  }
  private record Border(boolean isLeftBorder, int offset, @Nullable String text) implements Comparable<Border> {
    @Override
    public int compareTo(@NotNull Border o) {
      return offset < o.offset ? 1 : -1;
    }
  }

  private void testFoldingRegions(@NotNull String verificationFileName,
                                  @Nullable String destinationFileName,
                                  boolean doCheckCollapseStatus) {
    String expectedContent;
    File verificationFile;
    try {
      verificationFile = new File(verificationFileName);
      expectedContent = FileUtil.loadFile(verificationFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertNotNull(expectedContent);

    expectedContent = StringUtil.replace(expectedContent, "\r", "");
    String cleanContent = removeFoldingMarkers(expectedContent);
    if (destinationFileName == null) {
      String fileName = PathUtil.getFileName(verificationFileName);
      configureByText(fileName, cleanContent);
    }
    else {
      try {
        @SuppressWarnings("ConstantConditions") String tempDirPrefix = myTempDirFixture.getFile("").getUrl();

        // if destination (as a URL) points to a file inside the temp dir, then create it there
        if (destinationFileName.startsWith(tempDirPrefix)) {
          VirtualFile file = myTempDirFixture.createFile(destinationFileName.substring(tempDirPrefix.length()), cleanContent);
          assertNotNull(file);
          configureFromExistingVirtualFile(file);
        }
        else {
          FileUtil.writeToFile(new File(destinationFileName), cleanContent);
          VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(destinationFileName);
          assertNotNull(file);
          configureFromExistingVirtualFile(file);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    String actual = getFoldingDescription(doCheckCollapseStatus);
    if (!expectedContent.equals(actual)) {
      throw new FileComparisonFailedError(verificationFile.getName(), expectedContent, actual, verificationFile.getPath());
    }
  }

  @NotNull
  public static String removeFoldingMarkers(String expectedContent) {
    return expectedContent.replaceAll("<" + FOLD + "\\stext='[^']*'(\\sexpand='[^']*')*>", "")
      .replace("</" + FOLD + ">", "");
  }

  @Override
  public void testFoldingWithCollapseStatus(@NotNull String verificationFileName) {
    testFoldingRegions(verificationFileName, null, true);
  }

  @Override
  public void testFoldingWithCollapseStatus(@NotNull String verificationFileName, @Nullable String destinationFileName) {
    testFoldingRegions(verificationFileName, destinationFileName, true);
  }

  @Override
  public void testFolding(@NotNull String verificationFileName) {
    testFoldingRegions(verificationFileName, null, false);
  }

  @Override
  public void testRainbow(@NotNull String fileName, @NotNull String text, boolean isRainbowOn, boolean withColor) {
    String RB_PREFIF = "TEMP::RAINBOW_TEMP_";
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    boolean isRainbowOnInScheme = RainbowHighlighter.isRainbowEnabled(globalScheme, null);
    try {
      RainbowHighlighter.setRainbowEnabled(globalScheme, null, isRainbowOn);
      configureByText(fileName, text.replaceAll("<" + RAINBOW + "(\\scolor='[^']*')?>", "").replace("</" + RAINBOW + ">", ""));

      List<HighlightInfo> highlighting = ContainerUtil.filter(doHighlighting(), info -> info.type == RainbowHighlighter.RAINBOW_ELEMENT);
      assertEquals(text, getTagsFromSegments(editor.getDocument().getText(), highlighting, RAINBOW, highlightInfo -> {
        if (!withColor) {
          return null;
        }

        TextAttributesKey rb_key = highlightInfo.forcedTextAttributesKey;
        if (rb_key != null) {
          String name = rb_key.getExternalName();
          if (name.startsWith(RB_PREFIF)) {
            // Temp-rainbow key approach (current)
            int color = 0xff000001 + Integer.parseInt(name.substring(RB_PREFIF.length()));
            return "color='" + Integer.toHexString(color) + "'";
          }
        }

        // Fg-attributes approach (obsolete, but alternative)
        TextAttributes attributes = highlightInfo.getTextAttributes(null, null);
        String color = attributes == null ? "null"
                                          : attributes.getForegroundColor() == null
                                            ? "null"
                                            : Integer.toHexString(attributes.getForegroundColor().getRGB());
        return "color='" + color + "'";
      }));
    }
    finally {
      RainbowHighlighter.setRainbowEnabled(globalScheme, null, isRainbowOnInScheme);
    }
  }

  @Override
  public void testInlays() {
    InlayHintsChecker checker = new InlayHintsChecker(this);
    try {
      checker.setUp();
      checker.checkParameterHints();
    }
    finally {
      checker.tearDown();
    }
  }

  @Override
  public void testInlays(java.util.function.Function<? super Inlay<?>, String> inlayPresenter,
                         Predicate<? super Inlay<?>> inlayFilter) {
    InlayHintsChecker checker = new InlayHintsChecker(this);
    try {
      checker.setUp();
      checker.checkInlays(inlayPresenter::apply, inlayFilter::test);
    }
    finally {
      checker.tearDown();
    }
  }

  @Override
  public void checkResultWithInlays(String text) {
    Document checkDocument = new DocumentImpl(text);
    InlayHintsChecker checker = new InlayHintsChecker(this);
    CaretAndInlaysInfo inlaysAndCaretInfo = checker.extractInlaysAndCaretInfo(checkDocument);
    checkResult(checkDocument.getText());
    checker.verifyInlaysAndCaretInfo(inlaysAndCaretInfo, text);
  }

  @Override
  public void assertPreferredCompletionItems(int selected, String @NotNull ... expected) {
    myEditorTestFixture.assertPreferredCompletionItems(selected, expected);
  }

  @Override
  public void testStructureView(@NotNull Consumer<? super StructureViewComponent> consumer) {
    assertNotNull("configure first", myFile);

    FileEditor fileEditor = FileEditorManager.getInstance(getProject()).getSelectedEditor(myFile);
    assertNotNull("editor not opened for " + myFile, myFile);

    StructureViewBuilder builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(getFile());
    assertNotNull("no builder for " + myFile, builder);

    StructureViewComponent component = null;
    try {
      component = (StructureViewComponent)builder.createStructureView(fileEditor, getProject());
      PlatformTestUtil.waitForPromise(component.rebuildAndUpdate());
      consumer.consume(component);
    }
    finally {
      if (component != null) Disposer.dispose(component);
    }
  }

  @Override
  public void setCaresAboutInjection(boolean caresAboutInjection) {
    this.caresAboutInjection = caresAboutInjection;
  }

  @Override
  public void setReadEditorMarkupModel(boolean readEditorMarkupModel) {
    myReadEditorMarkupModel = readEditorMarkupModel;
  }

  @Override
  public LookupImpl getLookup() {
    return myEditorTestFixture.getLookup();
  }

  @NotNull
  @Override
  public List<Object> getGotoClassResults(@NotNull String pattern, boolean searchEverywhere, @Nullable PsiElement contextForSorting) {
    SearchEverywhereContributor<Object> contributor = createMockClassSearchEverywhereContributor(searchEverywhere);
    ArrayList<Object> results = new ArrayList<>();
    contributor.fetchElements(pattern, new MockProgressIndicator(), new CommonProcessors.CollectProcessor<>(results));
    return results;
  }

  @Override
  public @NotNull List<Object> getGotoSymbolResults(@NotNull String pattern,
                                                    boolean searchEverywhere,
                                                    @Nullable PsiElement contextForSorting) {
    SearchEverywhereContributor<Object> contributor = createMockSymbolSearchEverywhereContributor(searchEverywhere);
    ArrayList<Object> results = new ArrayList<>();
    contributor.fetchElements(pattern, new MockProgressIndicator(), new CommonProcessors.CollectProcessor<>(results));
    return results;
  }

  @NotNull
  @Override
  public List<Crumb> getBreadcrumbsAtCaret() {
    return myEditorTestFixture.getBreadcrumbsAtCaret();
  }

  private SearchEverywhereContributor<Object> createMockClassSearchEverywhereContributor(boolean everywhere) {
    DataContext dataContext = SimpleDataContext.getProjectContext(getProject());
    AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext);
    ClassSearchEverywhereContributor contributor = new ClassSearchEverywhereContributor(event) {{
      myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, everywhere));
    }};
    Disposer.register(getProjectDisposable(), contributor);
    return contributor;
  }

  private SearchEverywhereContributor<Object> createMockSymbolSearchEverywhereContributor(boolean everywhere) {
    DataContext dataContext = SimpleDataContext.getProjectContext(getProject());
    AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext);
    SymbolSearchEverywhereContributor contributor = new SymbolSearchEverywhereContributor(event) {{
      myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, everywhere));
    }};
    Disposer.register(getProjectDisposable(), contributor);
    return contributor;
  }

  protected void bringRealEditorBack() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    if (editor instanceof EditorWindow) {
      Document document = ((DocumentWindow)editor.getDocument()).getDelegate();
      setFileAndEditor(FileDocumentManager.getInstance().getFile(document), ((EditorWindow)editor).getDelegate());
    }
  }

  /**
   * Assert that the intention {@code action} is available on currently configured file/editor and invoke it.
   */
  public static void invokeIntention(@NotNull IntentionAction action, @NotNull PsiFile file, @NotNull Editor editor) {
    // Test that action will automatically clear the read-only attribute if modification is necessary.
    // If your test fails due to this, make sure that your quick-fix/intention
    // overrides "getElementToMakeWritable" or has the following line:
    // if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    Project project = file.getProject();
    VirtualFile vFile = Objects.requireNonNull(InjectedLanguageManager.getInstance(project).getTopLevelFile(file)).getVirtualFile();
    withReadOnlyFile(vFile, project, () -> {
      try {
        ApplicationManager.getApplication().invokeLater(() -> {
          try {
            boolean executed = ShowIntentionActionsHandler.chooseActionAndInvoke(file, editor, action, action.getText());
            if (!executed) {
              boolean available = action.isAvailable(project, editor, file);
              Assert.fail("Quick fix '" + action.getText() + "' (" + action.getClass()+")"+
                         " hasn't executed. isAvailable()="+available);
            }
          }
          catch (StubTextInconsistencyException e) {
            PsiTestUtil.compareStubTexts(e);
          }
        });
        UIUtil.dispatchAllInvocationEvents();
        checkPsiTextConsistency(project, vFile);
      }
      catch (AssertionError e) {
        ExceptionUtil.rethrowUnchecked(ExceptionUtil.getRootCause(e));
        throw e;
      }
    });
  }

  /**
   * Make the given file read-only and execute the given action, afterwards make file writable again
   * @return whether the action has made the file writable itself
   */
  public static boolean withReadOnlyFile(VirtualFile vFile, Project project, Runnable action) {
    if (PlatformUtils.isFleetBackend() && vFile.getFileSystem().isReadOnly()) {
      action.run();
      return false;
    }
    boolean writable;
    ReadonlyStatusHandlerImpl handler = (ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(project);
    setReadOnly(vFile, true);
    handler.setClearReadOnlyInTests(true);
    try {
      action.run();
    }
    finally {
      writable = vFile.isWritable();
      handler.setClearReadOnlyInTests(false);
      setReadOnly(vFile, false);
    }
    return writable;
  }

  private static void checkPsiTextConsistency(@NotNull Project project, @NotNull VirtualFile vFile) {
    PsiFile topLevelPsi = vFile.isValid() ? PsiManager.getInstance(project).findFile(vFile) : null;
    if (topLevelPsi != null) {
      if (Registry.is("ide.check.structural.psi.text.consistency.in.tests")) {
        PsiTestUtil.checkPsiStructureWithCommit(topLevelPsi, PsiTestUtil::checkPsiMatchesTextIgnoringNonCode);
      }
      else {
        PsiTestUtil.checkStubsMatchText(topLevelPsi);
      }
    }
  }

  private static void setReadOnly(VirtualFile vFile, boolean readOnlyStatus) {
    try {
      WriteAction.runAndWait(() -> ReadOnlyAttributeUtil.setReadOnlyAttribute(vFile, readOnlyStatus));
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @NotNull
  public String getUsageViewTreeTextRepresentation(@NotNull UsageViewImpl usageView) {
    Disposer.register(getTestRootDisposable(), usageView);
    usageView.expandAll();
    return TreeNodeTester.forNode(usageView.getRoot()).withPresenter(usageView::getNodeText).constructTextRepresentation();
  }

  private static final class SelectionAndCaretMarkupLoader {
    private final String fileText;
    private final String filePath;
    private final String newFileText;
    private final EditorTestUtil.CaretAndSelectionState caretState;

    private SelectionAndCaretMarkupLoader(@NotNull String fileText, String filePath) {
      this.fileText = fileText;
      this.filePath = filePath;
      Document document = EditorFactory.getInstance().createDocument(fileText);
      caretState = EditorTestUtil.extractCaretAndSelectionMarkers(document);
      newFileText = document.getText();
    }

    @NotNull
    private static SelectionAndCaretMarkupLoader fromFile(@NotNull String path, String charset) {
      return fromIoSource(() -> FileUtil.loadFile(new File(path), charset), path);
    }

    @NotNull
    private static SelectionAndCaretMarkupLoader fromFile(@NotNull VirtualFile file) {
      return fromIoSource(() -> VfsUtilCore.loadText(file), file.getPath());
    }

    @NotNull
    private static SelectionAndCaretMarkupLoader fromIoSource(@NotNull ThrowableComputable<String, IOException> source, String path) {
      try {
        return new SelectionAndCaretMarkupLoader(StringUtil.convertLineSeparators(source.compute()), path);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @NotNull
    private static SelectionAndCaretMarkupLoader fromText(@NotNull String text) {
      return new SelectionAndCaretMarkupLoader(text, null);
    }
  }

  @Override
  @NotNull
  public Disposable getProjectDisposable() {
    return myProjectFixture.getTestRootDisposable();
  }

  public enum SelectionAndCaretMarkupApplyPolicy {
    UPDATE_DOCUMENT_AND_LEAVE_IT_DIRTY,
    UPDATE_FILE_AND_KEEP_DOCUMENT_CLEAN
  }

  public void setSelectionAndCaretMarkupApplyPolicy(SelectionAndCaretMarkupApplyPolicy policy) {
    mySelectionAndCaretMarkupApplyPolicy = policy;
  }
}
