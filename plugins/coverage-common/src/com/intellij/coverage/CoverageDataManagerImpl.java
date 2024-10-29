// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.coverage.view.CoverageViewManager;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@ApiStatus.Internal
public class CoverageDataManagerImpl extends CoverageDataManager implements Disposable.Default {
  private final Project myProject;
  private final List<CoverageSuiteListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final Object myLock = new Object();
  private final Map<CoverageEngine, CoverageSuitesBundle> myActiveBundles = new ConcurrentHashMap<>();

  private boolean myIsProjectClosing = false;

  public CoverageDataManagerImpl(@NotNull Project project) {
    myProject = project;

    CoverageSuiteListener coverageViewListener = createCoverageViewListener();
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
        for (CoverageSuitesBundle bundle : myActiveBundles.values()) {
          chooseSuitesBundle(bundle);
        }
      }
    });
  }

  private void setUpRunnerEPRemovedCallback(@NotNull Project project) {
    CoverageRunner.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull CoverageRunner coverageRunner, @NotNull PluginDescriptor pluginDescriptor) {
        for (CoverageSuitesBundle suitesBundle : myActiveBundles.values()) {
          if (ContainerUtil.exists(suitesBundle.getSuites(), suite -> coverageRunner == suite.getRunner())) {
            closeSuitesBundle(suitesBundle);
          }
        }

        RunManager runManager = RunManager.getInstance(project);
        List<RunConfiguration> configurations = runManager.getAllConfigurationsList();
        for (RunConfiguration configuration : configurations) {
          if (configuration instanceof RunConfigurationBase<?> runConfiguration) {
            var coverageConfiguration = CoverageEnabledConfiguration.getOrNull(runConfiguration);
            if (coverageConfiguration != null) {
              coverageConfiguration.coverageRunnerExtensionRemoved(coverageRunner);
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
        CoverageSuitesBundle suitesBundle = myActiveBundles.get(coverageEngine);
        if (suitesBundle != null) {
          closeSuitesBundle(suitesBundle);
        }
      }
    }, this);
  }

  @Override
  public Collection<CoverageSuitesBundle> activeSuites() {
    return myActiveBundles.values();
  }

  @Override
  public CoverageSuitesBundle getCurrentSuitesBundle() {
    CoverageViewManager manager = CoverageViewManager.getInstanceIfCreated(myProject);
    if (manager != null) {
      CoverageSuitesBundle openedSuite = manager.getOpenedSuite();
      if (openedSuite != null) return openedSuite;
    }
    return myActiveBundles.values().stream().findFirst().orElse(null);
  }

  @Nullable
  protected CoverageSuiteListener createCoverageViewListener() {
    return new CoverageViewSuiteListener(myProject);
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
    CoverageDataSuitesManager manager = CoverageDataSuitesManager.getInstance(myProject);
    CoverageSuite suite = manager.createCoverageSuite(name, coverageRunner, fileProvider, lastCoverageTimeStamp);
    if (suite != null) {
      manager.addSuite(suite, suiteToMergeWith);
    }
    return suite;
  }

  /**
   * @see <a href="https://github.com/JetBrains/intellij-community/pull/2176">External request</a>
   */
  @SuppressWarnings("unused")
  public void addCoverageSuite(CoverageSuite suite, @Nullable String suiteToMergeWith) {
    CoverageDataSuitesManager.getInstance(myProject).addSuite(suite, suiteToMergeWith);
  }

  @Override
  public CoverageSuite addExternalCoverageSuite(@NotNull String selectedFileName,
                                                long timeStamp,
                                                @NotNull CoverageRunner coverageRunner,
                                                @NotNull CoverageFileProvider fileProvider) {
    return CoverageDataSuitesManager.getInstance(myProject)
      .addExternalCoverageSuite(selectedFileName, coverageRunner, fileProvider, timeStamp);
  }

  @Override
  public CoverageSuite addCoverageSuite(CoverageEnabledConfiguration config) {
    return CoverageDataSuitesManager.getInstance(myProject).addSuite(config);
  }

  @Override
  public CoverageSuite @NotNull [] getSuites() {
    return CoverageDataSuitesManager.getInstance(myProject).getSuites();
  }

  @Override
  public void removeCoverageSuite(CoverageSuite suite) {
    CoverageDataSuitesManager.getInstance(myProject).deleteSuite(suite);
    removeFromCurrent(suite);
  }

  @Override
  public void unregisterCoverageSuite(CoverageSuite suite) {
    CoverageDataSuitesManager.getInstance(myProject).removeSuite(suite);
    removeFromCurrent(suite);
  }

  private void removeFromCurrent(CoverageSuite suite) {
    Optional<CoverageSuitesBundle> containingBundle = myActiveBundles.values().stream().filter(b -> b.contains(suite)).findFirst();
    if (containingBundle.isPresent()) {
      CoverageSuitesBundle bundle = containingBundle.get();
      CoverageSuite[] suites = bundle.getSuites();
      if (suites.length > 1) {
        suites = ArrayUtil.remove(suites, suite);
        chooseSuitesBundle(new CoverageSuitesBundle(suites));
      }
      else {
        closeSuitesBundle(bundle);
      }
    }
  }

  // ==== Suites storage ====

  // ==== Sub coverage   ====

  @Override
  public boolean isSubCoverageActive() {
    return SubCoverageManager.getInstance(myProject).isSubCoverageActive();
  }

  @Override
  public void selectSubCoverage(@NotNull CoverageSuitesBundle suite, List<String> testNames) {
    SubCoverageManager.getInstance(myProject).selectSubCoverage(suite, testNames);
    reloadSuite(suite);
  }

  @Override
  public void restoreMergedCoverage(@NotNull CoverageSuitesBundle suite) {
    SubCoverageManager.getInstance(myProject).restoreMergedCoverage(suite);
    reloadSuite(suite);
  }

  private void reloadSuite(@NotNull CoverageSuitesBundle suite) {
    if (!myActiveBundles.containsKey(suite.getCoverageEngine())) return;
    fireBeforeSuiteChosen();
    CoverageDataAnnotationsManager.getInstance(myProject).clearAnnotations();
    suite.getCoverageEngine().getCoverageAnnotator(myProject).onSuiteChosen(suite);
    renewCoverageData(suite);
  }

  // ==== Sub coverage   ====

  @Override
  public void closeSuitesBundle(@NotNull CoverageSuitesBundle suite) {
    closeSuitesBundle(suite, true);
  }

  private void closeSuitesBundle(@NotNull CoverageSuitesBundle suite, boolean removeWatches) {
    if (!myActiveBundles.remove(suite.getCoverageEngine(), suite)) return;
    CoverageViewManager.getInstance(myProject).closeView(suite);
    if (removeWatches) {
      ExternalCoverageWatchManager.getInstance(myProject).clearWatches();
    }
    CoverageDataAnnotationsManager.getInstance(myProject).clearAnnotations();
    suite.getCoverageEngine().getCoverageAnnotator(myProject).onSuiteChosen(suite);
    suite.setCoverageData(null);
    triggerPresentationUpdate();
  }


  @Override
  public void chooseSuitesBundle(@NotNull CoverageSuitesBundle suite) {
    ExternalCoverageWatchManager.getInstance(myProject).clearWatches();
    updateCoverageData(suite);
  }

  void updateCoverageData(@NotNull CoverageSuitesBundle suite) {
    CoverageSuitesBundle currentSuite = myActiveBundles.get(suite.getCoverageEngine());
    if (currentSuite != null) {
      SubCoverageManager.getInstance(myProject).restoreMergedCoverage(currentSuite);
      closeSuitesBundle(currentSuite, false);
    }

    CoverageDataAnnotationsManager.getInstance(myProject).clearAnnotations();

    if (suite.ensureReportFilesExist()) {
      myActiveBundles.put(suite.getCoverageEngine(), suite);
      fireBeforeSuiteChosen();
      renewCoverageData(suite);
      fireAfterSuiteChosen();
    }
    else {
      triggerPresentationUpdate();
    }
  }

  @Override
  public void coverageGathered(@NotNull CoverageSuite suite) {
    fireCoverageGathered(suite);
    CoverageSuitesBundle bundle = myActiveBundles.get(suite.getCoverageEngine());
    if (bundle == null) {
      chooseSuitesBundle(new CoverageSuitesBundle(suite));
    }
    else {
      int replaceOption = CoverageOptionsProvider.getInstance(myProject).getOptionToReplace();
      boolean shouldAsk = replaceOption == CoverageOptionsProvider.ASK_ON_NEW_SUITE;
      if (shouldAsk) {
        ApplicationManager.getApplication().invokeLater(() -> openSuite(bundle, suite, askMergeOption(suite)));
      }
      else {
        openSuite(bundle, suite, replaceOption);
      }
    }
  }

  private void openSuite(CoverageSuitesBundle bundle, CoverageSuite suite, int option) {
    switch (option) {
      case CoverageOptionsProvider.REPLACE_SUITE -> chooseSuitesBundle(new CoverageSuitesBundle(suite));
      case CoverageOptionsProvider.ADD_SUITE -> chooseSuitesBundle(new CoverageSuitesBundle(ArrayUtil.append(bundle.getSuites(), suite)));
    }
  }

  private int askMergeOption(@NotNull CoverageSuite suite) {
    final CoverageOptionsProvider coverageOptionsProvider = CoverageOptionsProvider.getInstance(myProject);

    Function<Integer, Integer> mapCode = (Integer exitCode) -> {
      return switch (exitCode) {
        case MessageConstants.YES -> CoverageOptionsProvider.REPLACE_SUITE;
        case MessageConstants.NO -> CoverageOptionsProvider.ADD_SUITE;
        default -> CoverageOptionsProvider.IGNORE_SUITE;
      };
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
    int result = MessageDialogBuilder.yesNoCancel(title, message)
      .yesText(CoverageBundle.message("coverage.replace.active.suites"))
      .noText(CoverageBundle.message("coverage.add.to.active.suites"))
      .cancelText(CoverageBundle.message("coverage.do.not.apply.collected.coverage"))
      .doNotAsk(doNotAskOption)
      .show(suite.getProject());
    return mapCode.apply(result);
  }

  @Override
  public void triggerPresentationUpdate() {
    CoverageDataAnnotationsManager.getInstance(myProject).update();
    ApplicationManager.getApplication().invokeLater(() -> {
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

  public static void processGatheredCoverage(RunConfigurationBase<?> configuration) {
    final Project project = configuration.getProject();
    if (project.isDisposed()) return;
    final CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.getOrCreate(configuration);
    final CoverageSuite coverageSuite = coverageEnabledConfiguration.getCurrentCoverageSuite();
    if (coverageSuite != null) {
      ((BaseCoverageSuite)coverageSuite).setConfiguration(configuration);
      getInstance(project).coverageGathered(coverageSuite);
    }
  }

  protected void renewCoverageData(@NotNull CoverageSuitesBundle suite) {
    suite.getCoverageEngine().getCoverageAnnotator(myProject).renewCoverageData(suite, this);
  }

  @Override
  public <T> T doInReadActionIfProjectOpen(Computable<T> computation) {
    synchronized (myLock) {
      if (myIsProjectClosing) return null;
    }
    return ApplicationManager.getApplication().runReadAction(computation);
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

  public void fireCoverageDataCalculated(@NotNull CoverageSuitesBundle suitesBundle) {
    for (CoverageSuiteListener listener : myListeners) {
      listener.coverageDataCalculated(suitesBundle);
    }
  }

  @Override
  public void coverageDataCalculated(@NotNull CoverageSuitesBundle suitesBundle) {
    fireCoverageDataCalculated(suitesBundle);
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
