// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.coverage.view.CoverageViewSuiteListener;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageConstants;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;


public class CoverageDataManagerImpl extends CoverageDataManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(CoverageDataManagerImpl.class);

  private final Project myProject;
  private final CoverageDataSuitesManager mySuitesManager;
  private final List<CoverageSuiteListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final Object myLock = new Object();
  private CoverageSuitesBundle myCurrentSuitesBundle;

  private boolean myIsProjectClosing = false;
  private boolean mySubCoverageIsActive;

  private Set<LocalFileSystem.WatchRequest> myWatchRequests;
  private List<String> myCurrentSuiteRoots;
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



  public CoverageDataManagerImpl(@NotNull Project project) {
    myProject = project;
    mySuitesManager = new CoverageDataSuitesManager(project);
    Disposer.register(this, mySuitesManager);

    CoverageViewSuiteListener coverageViewListener = createCoverageViewListener();
    if (coverageViewListener != null) {
      addSuiteListener(coverageViewListener, this);
    }

    setUpOnSchemeChangeCallback(project);
    setUpRunnerEPRemovedCallback(project);
    setUpEngineEPRemovedCallback();
  }

  private void setUpOnSchemeChangeCallback(@NotNull Project project) {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        chooseSuitesBundle(myCurrentSuitesBundle);
      }
    });
  }

  private void setUpRunnerEPRemovedCallback(@NotNull Project project) {
    CoverageRunner.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull CoverageRunner coverageRunner, @NotNull PluginDescriptor pluginDescriptor) {
        CoverageSuitesBundle suitesBundle = getCurrentSuitesBundle();
        if (suitesBundle != null &&
            ContainerUtil.exists(suitesBundle.getSuites(), suite -> coverageRunner == suite.getRunner())) {
          chooseSuitesBundle(null);
        }

        RunManager runManager = RunManager.getInstance(project);
        List<RunConfiguration> configurations = runManager.getAllConfigurationsList();
        for (RunConfiguration configuration : configurations) {
          if (configuration instanceof RunConfigurationBase<?> runConfiguration) {
            CoverageEnabledConfiguration coverageEnabledConfiguration =
              runConfiguration.getCopyableUserData(CoverageEnabledConfiguration.COVERAGE_KEY);
            if (coverageEnabledConfiguration != null && Objects.equals(coverageRunner.getId(), coverageEnabledConfiguration.getRunnerId())) {
              coverageEnabledConfiguration.coverageRunnerExtensionRemoved(coverageRunner);
              runConfiguration.putCopyableUserData(CoverageEnabledConfiguration.COVERAGE_KEY, null);
            }
          }
        }

        //cleanup created templates
        ((RunManagerImpl)runManager).reloadSchemes();
      }
    }, this);
  }

  private void setUpEngineEPRemovedCallback() {
    CoverageEngine.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull CoverageEngine coverageEngine, @NotNull PluginDescriptor pluginDescriptor) {
        CoverageSuitesBundle suitesBundle = getCurrentSuitesBundle();
        if (suitesBundle != null && suitesBundle.getCoverageEngine() == coverageEngine) {
          chooseSuitesBundle(null);
        }
      }
    }, this);
  }

  @Override
  public CoverageSuitesBundle getCurrentSuitesBundle() {
    return myCurrentSuitesBundle;
  }

  @Nullable
  protected CoverageViewSuiteListener createCoverageViewListener() {
    return new CoverageViewSuiteListener(this, myProject);
  }

  // ==== Suites storage ====

  @Override
  public CoverageSuite addCoverageSuite(String name,
                                        @NotNull CoverageFileProvider fileProvider,
                                        String[] filters,
                                        long lastCoverageTimeStamp,
                                        @Nullable String suiteToMergeWith,
                                        @NotNull CoverageRunner coverageRunner,
                                        boolean coverageByTestEnabled,
                                        boolean branchCoverage) {
    return mySuitesManager.addSuite(coverageRunner, name, fileProvider, filters, lastCoverageTimeStamp, suiteToMergeWith,
                                    coverageByTestEnabled, branchCoverage);
  }

  /**
   * @see <a href="https://github.com/JetBrains/intellij-community/pull/2176">External request</a>
   */
  @SuppressWarnings("unused")
  public void addCoverageSuite(CoverageSuite suite, @Nullable String suiteToMergeWith) {
    mySuitesManager.addSuite(suite, suiteToMergeWith);
  }

  @Override
  public CoverageSuite addExternalCoverageSuite(@NotNull String selectedFileName,
                                                long timeStamp,
                                                @NotNull CoverageRunner coverageRunner,
                                                @NotNull CoverageFileProvider fileProvider) {
    return mySuitesManager.addExternalCoverageSuite(coverageRunner, selectedFileName, fileProvider, timeStamp);
  }

  @Override
  public CoverageSuite addCoverageSuite(CoverageEnabledConfiguration config) {
    return mySuitesManager.addSuite(config);
  }

  @Override
  public CoverageSuite @NotNull [] getSuites() {
    return mySuitesManager.getSuites();
  }

  @Override
  public void removeCoverageSuite(CoverageSuite suite) {
    mySuitesManager.deleteSuite(suite);
    removeFromCurrent(suite);
  }

  @Override
  public void unregisterCoverageSuite(CoverageSuite suite) {
    mySuitesManager.removeSuite(suite);
    removeFromCurrent(suite);
  }

  private void removeFromCurrent(CoverageSuite suite) {
    if (myCurrentSuitesBundle != null && myCurrentSuitesBundle.contains(suite)) {
      CoverageSuite[] suites = myCurrentSuitesBundle.getSuites();
      suites = ArrayUtil.remove(suites, suite);
      chooseSuitesBundle(suites.length > 0 ? new CoverageSuitesBundle(suites) : null);
    }
  }

  // ==== Suites storage ====

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
    CoverageDataAnnotationsManager.getInstance(myProject).clearAnnotations();

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
    fireCoverageGathered(suite);
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (myCurrentSuitesBundle != null) {
        final int replaceOption = CoverageOptionsProvider.getInstance(myProject).getOptionToReplace();
        final boolean canMergeSuites = myCurrentSuitesBundle.getCoverageEngine() == suite.getCoverageEngine();
        final boolean shouldAsk = replaceOption == CoverageOptionsProvider.ASK_ON_NEW_SUITE ||
                                  replaceOption == CoverageOptionsProvider.ADD_SUITE && !canMergeSuites;
        int actualOption = shouldAsk ? askMergeOption(suite, canMergeSuites) : replaceOption;
        switch (actualOption) {
          case CoverageOptionsProvider.REPLACE_SUITE -> chooseSuitesBundle(new CoverageSuitesBundle(suite));
          case CoverageOptionsProvider.ADD_SUITE ->
            chooseSuitesBundle(new CoverageSuitesBundle(ArrayUtil.append(myCurrentSuitesBundle.getSuites(), suite)));
        }
      }
      else {
        chooseSuitesBundle(new CoverageSuitesBundle(suite));
      }
    });
  }

  private int askMergeOption(@NotNull CoverageSuite suite, boolean canMergeSuites) {
    final CoverageOptionsProvider coverageOptionsProvider = CoverageOptionsProvider.getInstance(myProject);

    Function<Integer, Integer> mapCode = (Integer exitCode) -> {
      if (canMergeSuites) {
        return switch (exitCode) {
          case MessageConstants.YES -> CoverageOptionsProvider.REPLACE_SUITE;
          case MessageConstants.NO -> CoverageOptionsProvider.ADD_SUITE;
          default -> CoverageOptionsProvider.IGNORE_SUITE;
        };
      }
      else {
        return exitCode == 0 ? CoverageOptionsProvider.REPLACE_SUITE : CoverageOptionsProvider.IGNORE_SUITE;
      }
    };

    var doNotAskOption = new DoNotAskOption.Adapter() {
      @Override
      public void rememberChoice(boolean isSelected, int exitCode) {
        if (isSelected) {
          coverageOptionsProvider.setOptionsToReplace(mapCode.apply(exitCode));
        }
      }
    };
    String title = CoverageBundle.message("code.coverage");
    String message = CoverageBundle.message("display.coverage.prompt", suite.getPresentableName());
    if (canMergeSuites) {
      int result = MessageDialogBuilder.yesNoCancel(title, message)
        .yesText(CoverageBundle.message("coverage.replace.active.suites"))
        .noText(CoverageBundle.message("coverage.add.to.active.suites"))
        .cancelText(CoverageBundle.message("coverage.do.not.apply.collected.coverage"))
        .doNotAsk(doNotAskOption)
        .show(suite.getProject());
      return mapCode.apply(result);
    }
    else {
      return MessageDialogBuilder.yesNo(title, message)
               .yesText(CoverageBundle.message("coverage.replace.active.suites"))
               .noText(CoverageBundle.message("coverage.do.not.apply.collected.coverage"))
               .doNotAsk(doNotAskOption)
               .ask(suite.getProject()) ? CoverageOptionsProvider.REPLACE_SUITE : CoverageOptionsProvider.IGNORE_SUITE;
    }
  }

  @Override
  public void triggerPresentationUpdate() {
    CoverageDataAnnotationsManager.getInstance(myProject).update();
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed()) return;
      ProjectView.getInstance(myProject).refresh();
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
        handler.removeProcessListener(this);
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
  public void addRootsToWatch(List<? extends CoverageSuite> suites) {
    myCurrentSuiteRoots = ContainerUtil.map(suites, suite -> suite.getCoverageDataFileName());
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    myCurrentSuiteRoots.forEach(path -> fileSystem.refreshAndFindFileByPath(path));
    myWatchRequests = fileSystem.addRootsToWatch(myCurrentSuiteRoots, true);
    VirtualFileManager.getInstance().addVirtualFileListener(myContentListener);
  }

  @Override
  public void dispose() { }

  public static void processGatheredCoverage(RunConfigurationBase<?> configuration) {
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

  @Override
  public <T> T doInReadActionIfProjectOpen(Computable<T> computation) {
    synchronized (myLock) {
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
      suite.getCoverageEngine().collectTestLines(testNames, coverageSuite, executionTrace);
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

  @Override
  public void restoreMergedCoverage(@NotNull final CoverageSuitesBundle suite) {
    mySubCoverageIsActive = false;
    suite.restoreCoverageData();
    renewCoverageData(suite);
  }

  @Override
  public void addSuiteListener(@NotNull final CoverageSuiteListener listener, @NotNull Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  public void fireCoverageGathered(@NotNull CoverageSuite suite) {
    for (CoverageSuiteListener listener : myListeners) {
      listener.coverageGathered(suite);
    }
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

  public void fireCoverageDataCalculated() {
    for (CoverageSuiteListener listener : myListeners) {
      listener.coverageDataCalculated();
    }
  }

  @Override
  public void coverageDataCalculated() {
    fireCoverageDataCalculated();
  }

  @Override
  public boolean isSubCoverageActive() {
    return mySubCoverageIsActive;
  }

  public static class CoverageProjectManagerListener implements ProjectCloseListener {
    @Override
    public void projectClosing(@NotNull Project project) {
      CoverageDataManagerImpl manager = (CoverageDataManagerImpl)getInstance(project);
      synchronized (manager.myLock) {
        manager.myIsProjectClosing = true;
      }
    }
  }
}
