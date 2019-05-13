// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.coverage.view.CoverageViewManager;
import com.intellij.coverage.view.CoverageViewSuiteListener;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * @author ven
 */
public class CoverageDataManagerImpl extends CoverageDataManager {
  private static final String REPLACE_ACTIVE_SUITES = "&Replace active suites";
  private static final String ADD_TO_ACTIVE_SUITES = "&Add to active suites";
  private static final String DO_NOT_APPLY_COLLECTED_COVERAGE = "Do not apply &collected coverage";

  private final List<CoverageSuiteListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final Logger LOG = Logger.getInstance(CoverageDataManagerImpl.class);
  @NonNls
  private static final String SUITE = "SUITE";

  private final Project myProject;
  private final Set<CoverageSuite> myCoverageSuites = new HashSet<>();
  private boolean myIsProjectClosing = false;

  private final Object myLock = new Object();
  private boolean mySubCoverageIsActive;

  private final VirtualFileContentsChangedAdapter myContentListener = new VirtualFileContentsChangedAdapter() {
    @Override
    protected void onFileChange(@NotNull VirtualFile fileOrDirectory) {
      if (myCurrentSuiteRoots != null && VfsUtilCore.isUnder(fileOrDirectory.getPath(), myCurrentSuiteRoots)) {
        myCurrentSuitesBundle.restoreCoverageData();
        updateCoverageData(myCurrentSuitesBundle);
      }
    }

    @Override
    protected void onBeforeFileChange(@NotNull VirtualFile fileOrDirectory) { }
  };
  private Set<LocalFileSystem.WatchRequest> myWatchRequests;
  private List<String> myCurrentSuiteRoots;

  @Override
  public CoverageSuitesBundle getCurrentSuitesBundle() {
    return myCurrentSuitesBundle;
  }

  private CoverageSuitesBundle myCurrentSuitesBundle;

  private final Object ANNOTATORS_LOCK = new Object();
  private final Map<Editor, SrcFileAnnotator> myAnnotators = new HashMap<>();

  public CoverageDataManagerImpl(@NotNull Project project) {
    myProject = project;
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        chooseSuitesBundle(myCurrentSuitesBundle);
      }
    });
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        if (project == myProject) {
          EditorFactory.getInstance().addEditorFactoryListener(new CoverageEditorFactoryListener(), myProject);
        }
      }

      @Override
      public void projectClosing(@NotNull Project project) {
        if (project != myProject) {
          return;
        }

        synchronized (myLock) {
          myIsProjectClosing = true;
        }
      }
    });

    final CoverageViewSuiteListener coverageViewListener = createCoverageViewListener();
    if (coverageViewListener != null) {
      addSuiteListener(coverageViewListener, myProject);
    }
  }

  @Nullable
  protected CoverageViewSuiteListener createCoverageViewListener() {
    return new CoverageViewSuiteListener(this, myProject);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    for (Element suiteElement : element.getChildren(SUITE)) {
      final CoverageRunner coverageRunner = BaseCoverageSuite.readRunnerAttribute(suiteElement);
      // skip unknown runners
      if (coverageRunner == null) {
        // collect gc
        final CoverageFileProvider fileProvider = BaseCoverageSuite.readDataFileProviderAttribute(suiteElement);
        if (fileProvider.isValid()) {
          //deleteCachedCoverage(fileProvider.getCoverageDataFilePath());
        }
        continue;
      }

      CoverageSuite suite = null;
      for (CoverageEngine engine : CoverageEngine.EP_NAME.getExtensions()) {
        if (coverageRunner.acceptsCoverageEngine(engine)) {
          suite = engine.createEmptyCoverageSuite(coverageRunner);
          if (suite != null) {
            if (suite instanceof BaseCoverageSuite) {
              ((BaseCoverageSuite)suite).setProject(myProject);
            }
            break;
          }
        }
      }
      if (suite != null) {
        try {
          suite.readExternal(suiteElement);
          myCoverageSuites.add(suite);
        }
        catch (NumberFormatException e) {
          //try next suite
        }
      }
    }
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    for (CoverageSuite coverageSuite : myCoverageSuites) {
      final Element suiteElement = new Element(SUITE);
      element.addContent(suiteElement);
      coverageSuite.writeExternal(suiteElement);
    }
  }

  @Override
  public CoverageSuite addCoverageSuite(final String name, final CoverageFileProvider fileProvider, final String[] filters, final long lastCoverageTimeStamp,
                                        @Nullable final String suiteToMergeWith,
                                        final CoverageRunner coverageRunner,
                                        final boolean collectLineInfo,
                                        final boolean tracingEnabled) {
    final CoverageSuite suite = createCoverageSuite(coverageRunner, name, fileProvider, filters, lastCoverageTimeStamp, suiteToMergeWith, collectLineInfo, tracingEnabled);
    if (suiteToMergeWith == null || !name.equals(suiteToMergeWith)) {
      removeCoverageSuite(suite);
    }
    myCoverageSuites.remove(suite); // remove previous instance
    myCoverageSuites.add(suite); // add new instance
    return suite;
  }

  @Override
  public CoverageSuite addExternalCoverageSuite(String selectedFileName,
                                                long timeStamp,
                                                CoverageRunner coverageRunner,
                                                CoverageFileProvider fileProvider) {
    final CoverageSuite suite = createCoverageSuite(coverageRunner, selectedFileName, fileProvider, ArrayUtil.EMPTY_STRING_ARRAY, timeStamp, null, false, false);
    myCoverageSuites.add(suite);
    return suite;
  }

  @Override
  public CoverageSuite addCoverageSuite(final CoverageEnabledConfiguration config) {
    final String name = config.getName() + " Coverage Results";
    final String covFilePath = config.getCoverageFilePath();
    assert covFilePath != null; // Shouldn't be null here!

    final CoverageRunner coverageRunner = config.getCoverageRunner();
    LOG.assertTrue(coverageRunner != null, "Coverage runner id = " + config.getRunnerId());

    final DefaultCoverageFileProvider fileProvider = new DefaultCoverageFileProvider(new File(covFilePath));
    final CoverageSuite suite = createCoverageSuite(config, name, coverageRunner, fileProvider);

    // remove previous instance
    removeCoverageSuite(suite);

    // add new instance
    myCoverageSuites.add(suite);
    return suite;
  }

  @Override
  public void removeCoverageSuite(final CoverageSuite suite) {
    final String fileName = suite.getCoverageDataFileName();

    boolean deleteTraces = suite.isTracingEnabled();
    if (!FileUtil.isAncestor(PathManager.getSystemPath(), fileName, false)) {
      String message = "Would you like to delete file \'" + fileName + "\' ";
      if (deleteTraces) {
        message += "and traces directory \'" + FileUtil.getNameWithoutExtension(new File(fileName)) + "\' ";
      }
      message += "on disk?";
      if (Messages.showYesNoDialog(myProject, message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) == Messages.YES) {
        deleteCachedCoverage(fileName, deleteTraces);
      }
    } else {
      deleteCachedCoverage(fileName, deleteTraces);
    }

    myCoverageSuites.remove(suite);
    if (myCurrentSuitesBundle != null && myCurrentSuitesBundle.contains(suite)) {
      CoverageSuite[] suites = myCurrentSuitesBundle.getSuites();
      suites = ArrayUtil.remove(suites, suite);
      chooseSuitesBundle(suites.length > 0 ? new CoverageSuitesBundle(suites) : null);
    }
  }

  private static void deleteCachedCoverage(String coverageDataFileName, boolean deleteTraces) {
    FileUtil.delete(new File(coverageDataFileName));
    if (deleteTraces) {
      FileUtil.delete(getTracesDirectory(coverageDataFileName));
    }
  }

  @Override
  @NotNull
  public CoverageSuite[] getSuites() {
    return myCoverageSuites.toArray(new CoverageSuite[0]);
  }

  @Override
  public void chooseSuitesBundle(final CoverageSuitesBundle suite) {
    if (myCurrentSuitesBundle == suite && suite == null) {
      return;
    }

    if (myWatchRequests != null) {
      LocalFileSystem.getInstance().removeWatchedRoots(myWatchRequests);
      VirtualFileManager.getInstance().removeVirtualFileListener(myContentListener);

      myWatchRequests = null;
      myCurrentSuiteRoots = null;
    }

    updateCoverageData(suite);
  }

  private void updateCoverageData(CoverageSuitesBundle suite) {
    LOG.assertTrue(!myProject.isDefault());

    fireBeforeSuiteChosen();

    mySubCoverageIsActive = false;
    if (myCurrentSuitesBundle != null) {
      myCurrentSuitesBundle.getCoverageEngine().getCoverageAnnotator(myProject).onSuiteChosen(suite);
    }

    myCurrentSuitesBundle = suite;
    disposeAnnotators();

    if (suite == null) {
      triggerPresentationUpdate();
      return;
    }

    for (CoverageSuite coverageSuite : myCurrentSuitesBundle.getSuites()) {
      final boolean suiteFileExists = coverageSuite.getCoverageDataFileProvider().ensureFileExists();
      if (!suiteFileExists) {
        chooseSuitesBundle(null);
        return;
      }
    }

    renewCoverageData(suite);

    fireAfterSuiteChosen();
  }

  @Override
  public void coverageGathered(@NotNull final CoverageSuite suite) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (myCurrentSuitesBundle != null) {
        final String message = CodeInsightBundle.message("display.coverage.prompt", suite.getPresentableName());

        final CoverageOptionsProvider coverageOptionsProvider = CoverageOptionsProvider.getInstance(myProject);
        final DialogWrapper.DoNotAskOption doNotAskOption = new DialogWrapper.DoNotAskOption() {
          @Override
          public boolean isToBeShown() {
            return coverageOptionsProvider.getOptionToReplace() == 3;
          }

          @Override
          public void setToBeShown(boolean value, int exitCode) {
            coverageOptionsProvider.setOptionsToReplace(value ? 3 : exitCode);
          }

          @Override
          public boolean canBeHidden() {
            return true;
          }

          @Override
          public boolean shouldSaveOptionsOnCancel() {
            return true;
          }

          @NotNull
          @Override
          public String getDoNotShowMessage() {
            return CommonBundle.message("dialog.options.do.not.show");
          }
        };
        final String[] options = myCurrentSuitesBundle.getCoverageEngine() == suite.getCoverageEngine() ?
                                 new String[] {REPLACE_ACTIVE_SUITES, ADD_TO_ACTIVE_SUITES, DO_NOT_APPLY_COLLECTED_COVERAGE} :
                                 new String[] {REPLACE_ACTIVE_SUITES, DO_NOT_APPLY_COLLECTED_COVERAGE};
        final int answer = doNotAskOption.isToBeShown() ? Messages.showDialog(message, CodeInsightBundle.message("code.coverage"),
                                                                              options, 1, Messages.getQuestionIcon(),
                                                                              doNotAskOption) : coverageOptionsProvider.getOptionToReplace();
        if (answer == DialogWrapper.OK_EXIT_CODE) {
          chooseSuitesBundle(new CoverageSuitesBundle(suite));
        }
        else if (answer == 1) {
          chooseSuitesBundle(new CoverageSuitesBundle(ArrayUtil.append(myCurrentSuitesBundle.getSuites(), suite)));
        }
      }
      else {
        chooseSuitesBundle(new CoverageSuitesBundle(suite));
      }
    });
  }

  @Override
  public void triggerPresentationUpdate() {
    renewInformationInEditors();
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed()) return;
      ProjectView.getInstance(myProject).refresh();
      CoverageViewManager.getInstance(myProject).setReady(true);
    });
  }

  @Override
  public void attachToProcess(@NotNull final ProcessHandler handler,
                              @NotNull final RunConfigurationBase configuration,
                              final RunnerSettings runnerSettings) {
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull final ProcessEvent event) {
        processGatheredCoverage(configuration, runnerSettings);
      }
    });
  }

  @Override
  public void processGatheredCoverage(@NotNull RunConfigurationBase configuration, RunnerSettings runnerSettings) {
    if (runnerSettings instanceof CoverageRunnerData) {
      processGatheredCoverage(configuration);
    }
  }

  /**
   * Called from EDT, on external coverage suite choosing
   */
  public void addRootsToWatch(List<CoverageSuite> suites) {
    myCurrentSuiteRoots = ContainerUtil.map(suites, suite -> suite.getCoverageDataFileName());
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    myCurrentSuiteRoots.forEach(path -> fileSystem.refreshAndFindFileByPath(path));
    myWatchRequests = fileSystem.addRootsToWatch(myCurrentSuiteRoots, true);
    VirtualFileManager.getInstance().addVirtualFileListener(myContentListener);
  }

  public static void processGatheredCoverage(RunConfigurationBase configuration) {
    final Project project = configuration.getProject();
    if (project.isDisposed()) return;
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);
    final CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.getOrCreate(configuration);
    final CoverageSuite coverageSuite = coverageEnabledConfiguration.getCurrentCoverageSuite();
    if (coverageSuite != null) {
      ((BaseCoverageSuite)coverageSuite).setConfiguration(configuration);
      coverageDataManager.coverageGathered(coverageSuite);
    }
  }

  protected void renewCoverageData(@NotNull final CoverageSuitesBundle suite) {
    if (myCurrentSuitesBundle != null) {
      myCurrentSuitesBundle.getCoverageEngine().getCoverageAnnotator(myProject).renewCoverageData(suite, this);
    }
  }

  private void renewInformationInEditors() {
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    final VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      final FileEditor[] allEditors = fileEditorManager.getAllEditors(openFile);
      applyInformationToEditor(allEditors, openFile);
    }
  }

  private void applyInformationToEditor(FileEditor[] editors, final VirtualFile file) {
    final PsiFile psiFile = doInReadActionIfProjectOpen(() -> PsiManager.getInstance(myProject).findFile(file));
    if (psiFile != null && myCurrentSuitesBundle != null && psiFile.isPhysical()) {
      final CoverageEngine engine = myCurrentSuitesBundle.getCoverageEngine();
      if (!engine.coverageEditorHighlightingApplicableTo(psiFile)) {
        return;
      }

      for (FileEditor editor : editors) {
        if (editor instanceof TextEditor) {
          final Editor textEditor = ((TextEditor)editor).getEditor();
          SrcFileAnnotator annotator;
          synchronized (ANNOTATORS_LOCK) {
            annotator = myAnnotators.remove(textEditor);
          }
          if (annotator != null) {
            Disposer.dispose(annotator);
          }
          break;
        }
      }

      for (FileEditor editor : editors) {
        if (editor instanceof TextEditor) {
          final Editor textEditor = ((TextEditor)editor).getEditor();
          SrcFileAnnotator annotator = getAnnotator(textEditor);
          if (annotator == null) {
            annotator = new SrcFileAnnotator(psiFile, textEditor);
            synchronized (ANNOTATORS_LOCK) {
              myAnnotators.put(textEditor, annotator);
            }
          }

          if (myCurrentSuitesBundle != null && engine.acceptedByFilters(psiFile, myCurrentSuitesBundle)) {
            annotator.showCoverageInformation(myCurrentSuitesBundle);
          }
        }
      }
    }
  }

  @Override
  public <T> T doInReadActionIfProjectOpen(Computable<T> computation) {
    synchronized(myLock) {
      if (myIsProjectClosing) return null;
    }
    return ApplicationManager.getApplication().runReadAction(computation);
  }

  @Override
  public void selectSubCoverage(@NotNull final CoverageSuitesBundle suite, final List<String> testNames) {
    suite.restoreCoverageData();
    final ProjectData data = suite.getCoverageData();
    if (data == null) return;
    mySubCoverageIsActive = true;
    final Map<String, Set<Integer>> executionTrace = new HashMap<>();
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final String fileName = coverageSuite.getCoverageDataFileName();
      final File tracesDir = getTracesDirectory(fileName);
      for (String testName : testNames) {
        final File file = new File(tracesDir, FileUtil.sanitizeFileName(testName) + ".tr");
        if (file.exists()) {
          try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            int traceSize = in.readInt();
            for (int i = 0; i < traceSize; i++) {
              final String className = in.readUTF();
              final int linesSize = in.readInt();
              Set<Integer> lines = executionTrace.get(className);
              if (lines == null) {
                lines = new HashSet<>();
                executionTrace.put(className, lines);
              }
              for(int l = 0; l < linesSize; l++) {
                lines.add(in.readInt());
              }
            }
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      }
    }
    final ProjectData projectData = new ProjectData();
    for (String className : executionTrace.keySet()) {
      ClassData loadedClassData = projectData.getClassData(className);
      if (loadedClassData == null) {
        loadedClassData = projectData.getOrCreateClassData(className);
      }
      final Set<Integer> lineNumbers = executionTrace.get(className);
      final ClassData oldData = data.getClassData(className);
      LOG.assertTrue(oldData != null, "missed className: \"" + className + "\"");
      final Object[] oldLines = oldData.getLines();
      LOG.assertTrue(oldLines != null);
      int maxNumber = oldLines.length;
      for (Integer lineNumber : lineNumbers) {
        if (lineNumber >= maxNumber) {
          maxNumber = lineNumber + 1;
        }
      }
      final LineData[] lines = new LineData[maxNumber];
      for (Integer line : lineNumbers) {
        final int lineIdx = line.intValue() - 1;
        String methodSig = null;
        if (lineIdx < oldData.getLines().length) {
          final LineData oldLineData = oldData.getLineData(lineIdx);
          if (oldLineData != null) {
            methodSig = oldLineData.getMethodSignature();
          }
        }
        final LineData lineData = new LineData(lineIdx, methodSig);
        if (methodSig != null) {
          loadedClassData.registerMethodSignature(lineData);
        }
        lineData.setStatus(LineCoverage.FULL);
        lines[lineIdx] = lineData;
      }
      loadedClassData.setLines(lines);
    }
    suite.setCoverageData(projectData);
    renewCoverageData(suite);
  }

  private static File getTracesDirectory(final String fileName) {
    return new File(new File(fileName).getParentFile(), FileUtil.getNameWithoutExtension(new File(fileName)));
  }

  @Override
  public void restoreMergedCoverage(@NotNull final CoverageSuitesBundle suite) {
    mySubCoverageIsActive = false;
    suite.restoreCoverageData();
    renewCoverageData(suite);
  }

  @Override
  public void addSuiteListener(final CoverageSuiteListener listener, Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  public void fireBeforeSuiteChosen() {
    for (CoverageSuiteListener listener : myListeners) {
      listener.beforeSuiteChosen();
    }
  }

  public void fireAfterSuiteChosen() {
    for (CoverageSuiteListener listener : myListeners) {
      listener.afterSuiteChosen();
    }
  }

  @Override
  public boolean isSubCoverageActive() {
    return mySubCoverageIsActive;
  }

  @Nullable
  public SrcFileAnnotator getAnnotator(Editor editor) {
    synchronized (ANNOTATORS_LOCK) {
      return myAnnotators.get(editor);
    }
  }

  public void disposeAnnotators() {
    synchronized (ANNOTATORS_LOCK) {
      for (SrcFileAnnotator annotator : myAnnotators.values()) {
        if (annotator != null) {
          Disposer.dispose(annotator);
        }
      }
      myAnnotators.clear();
    }
  }

  @NotNull
  private static CoverageSuite createCoverageSuite(final CoverageEnabledConfiguration config,
                                                   final String name,
                                                   final CoverageRunner coverageRunner,
                                                   final DefaultCoverageFileProvider fileProvider) {
    CoverageSuite suite = null;
    for (CoverageEngine engine : CoverageEngine.EP_NAME.getExtensions()) {
      if (coverageRunner.acceptsCoverageEngine(engine) && engine.isApplicableTo(config.getConfiguration())) {
        suite = engine.createCoverageSuite(coverageRunner, name, fileProvider, config);
        if (suite != null) {
          break;
        }
      }
    }
    LOG.assertTrue(suite != null, "Cannot create coverage suite for runner: " + coverageRunner.getPresentableName());
    return suite;
  }

  @NotNull
  private CoverageSuite createCoverageSuite(final CoverageRunner coverageRunner,
                                            final String name,
                                            final CoverageFileProvider fileProvider,
                                            final String[] filters,
                                            final long lastCoverageTimeStamp,
                                            final String suiteToMergeWith,
                                            final boolean collectLineInfo,
                                            final boolean tracingEnabled) {

    CoverageSuite suite = null;
    for (CoverageEngine engine : CoverageEngine.EP_NAME.getExtensions()) {
      if (coverageRunner.acceptsCoverageEngine(engine)) {
        suite = engine.createCoverageSuite(coverageRunner, name, fileProvider, filters, lastCoverageTimeStamp,
                                           suiteToMergeWith, collectLineInfo, tracingEnabled, false, myProject);
        if (suite != null) {
          break;
        }
      }
    }

    LOG.assertTrue(suite != null, "Cannot create coverage suite for runner: " + coverageRunner.getPresentableName());
    return suite;
  }

  private class CoverageEditorFactoryListener implements EditorFactoryListener {
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myProject);
    private final Map<Editor, Runnable> myCurrentEditors = new HashMap<>();

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
      synchronized (myLock) {
        if (myIsProjectClosing) return;
      }

      final Editor editor = event.getEditor();
      if (editor.getProject() != myProject) return;
      final PsiFile psiFile = ReadAction.compute(() -> {
        if (myProject.isDisposed()) return null;
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        final Document document = editor.getDocument();
        return documentManager.getPsiFile(document);
      });

      if (psiFile != null && myCurrentSuitesBundle != null && psiFile.isPhysical()) {
        final CoverageEngine engine = myCurrentSuitesBundle.getCoverageEngine();
        if (!engine.coverageEditorHighlightingApplicableTo(psiFile)) {
          return;
        }

        SrcFileAnnotator annotator = getAnnotator(editor);
        if (annotator == null) {
          annotator = new SrcFileAnnotator(psiFile, editor);
        }

        final SrcFileAnnotator finalAnnotator = annotator;

        synchronized (ANNOTATORS_LOCK) {
          myAnnotators.put(editor, finalAnnotator);
        }

        final Runnable request = () -> {
          if (myProject.isDisposed()) return;
          if (myCurrentSuitesBundle != null) {
            if (engine.acceptedByFilters(psiFile, myCurrentSuitesBundle)) {
              finalAnnotator.showCoverageInformation(myCurrentSuitesBundle);
            }
          }
        };
        myCurrentEditors.put(editor, request);
        myAlarm.addRequest(request, 100);
      }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      if (editor.getProject() != myProject) return;
      try {
        final SrcFileAnnotator fileAnnotator;
        synchronized (ANNOTATORS_LOCK) {
          fileAnnotator = myAnnotators.remove(editor);
        }
        if (fileAnnotator != null) {
          Disposer.dispose(fileAnnotator);
        }
      }
      finally {
        final Runnable request = myCurrentEditors.remove(editor);
        if (request != null) {
          myAlarm.cancelRequest(request);
        }
      }
    }
  }
}
