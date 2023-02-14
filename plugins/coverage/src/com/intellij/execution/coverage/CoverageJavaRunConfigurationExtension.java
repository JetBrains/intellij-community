// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.coverage;

import com.intellij.coverage.*;
import com.intellij.coverage.listeners.java.CoverageListener;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.Location;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.JavaTargetDependentParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageConfigurable;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.CoverageFragment;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Registers "Coverage" tab in Java run configurations
 */
public class CoverageJavaRunConfigurationExtension extends RunConfigurationExtension {

  private JavaTargetDependentParameters myTargetDependentParameters;

  @Override
  public void attachToProcess(@NotNull final RunConfigurationBase configuration, @NotNull ProcessHandler handler, RunnerSettings runnerSettings) {
    if (myTargetDependentParameters == null || myTargetDependentParameters.getTargetEnvironment() == null) {
      CoverageDataManager.getInstance(configuration.getProject()).attachToProcess(handler, configuration, runnerSettings);
      return;
    }

    if (runnerSettings instanceof CoverageRunnerData) {
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull final ProcessEvent event) {
          new Task.Backgroundable(configuration.getProject(),
                                  JavaCoverageBundle.message("download.coverage.report.from.target.progress.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              JavaCoverageEnabledConfiguration coverageConfiguration =
                (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(configuration);
              try {
                coverageConfiguration.downloadReport(myTargetDependentParameters.getTargetEnvironment(), indicator);
              }
              catch (Exception ignored) {
                Notifications.Bus.notify(new Notification("Coverage",
                                                          CoverageBundle.message("coverage.error.collecting.data.text"),
                                                          JavaCoverageBundle.message("download.coverage.report.from.target.failed"),
                                                          NotificationType.ERROR));
                return;
              }
              finally {
                myTargetDependentParameters = null;
              }
              CoverageDataManager.getInstance(configuration.getProject()).processGatheredCoverage(configuration, runnerSettings);
            }
          }.queue();
          handler.removeProcessListener(this);
        }
      });
    }
  }

  @Override
  @Nullable
  public SettingsEditor createEditor(@NotNull RunConfigurationBase configuration) {
    return new CoverageConfigurable(configuration);
  }

  @Override
  protected <P extends RunConfigurationBase<?>> List<SettingsEditor<P>> createFragments(@NotNull P configuration) {
    return Collections.singletonList(new CoverageFragment<>(configuration));
  }

  @Override
  public String getEditorTitle() {
    return CoverageEngine.getEditorTitle();
  }

  @NotNull
  @Override
  public String getSerializationId() {
    return "coverage";
  }

  @Override
  public void updateJavaParameters(@NotNull RunConfigurationBase configuration, @NotNull JavaParameters params, RunnerSettings runnerSettings) {
    if (!isApplicableFor(configuration)) {
      return;
    }

    final JavaCoverageEnabledConfiguration coverageConfig = JavaCoverageEnabledConfiguration.getFrom(configuration);
    if (coverageConfig == null) return;
    coverageConfig.setCurrentCoverageSuite(null);
    final CoverageRunner coverageRunner = coverageConfig.getCoverageRunner();
    if (runnerSettings instanceof CoverageRunnerData && coverageRunner != null) {
      Project project = configuration.getProject();
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);
      ApplicationManager.getApplication().invokeLater(() -> {
        coverageConfig.setCurrentCoverageSuite(coverageDataManager.addCoverageSuite(coverageConfig));
      }, ModalityState.NON_MODAL, project.getDisposed());
      appendCoverageArgument(configuration, params, coverageConfig);

      final Sdk jdk = params.getJdk();
      if (jdk != null && JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_7) && coverageRunner instanceof JavaCoverageRunner && !((JavaCoverageRunner)coverageRunner).isJdk7Compatible()) {
        Notifications.Bus.notify(new Notification("Coverage",
                                                  JavaCoverageBundle.message("coverage.instrumentation.jdk7.compatibility"),
                                                  JavaCoverageBundle.message(
                                                    "coverage.instrumentation.jdk7.compatibility.veryfy.error.warning",
                                                    coverageRunner.getPresentableName()),
                                                  NotificationType.WARNING));
      }
    }
  }

  private void appendCoverageArgument(@NotNull RunConfigurationBase configuration,
                                      @NotNull JavaParameters params,
                                      JavaCoverageEnabledConfiguration coverageConfig) {
    JavaParameters coverageParams = new JavaParameters();
    coverageConfig.appendCoverageArgument(configuration, coverageParams);

    boolean runsUnderNonLocalTarget = configuration instanceof TargetEnvironmentAwareRunProfile
                                      && ((TargetEnvironmentAwareRunProfile)configuration).needPrepareTarget();
    if (!runsUnderNonLocalTarget) {
      params.getVMParametersList().addAll(coverageParams.getTargetDependentParameters().toLocalParameters());
      myTargetDependentParameters = null;
    }
    else {
      coverageParams.getTargetDependentParameters().asTargetParameters().forEach(params.getTargetDependentParameters().asTargetParameters()::add);
      myTargetDependentParameters = params.getTargetDependentParameters();
    }
  }

  @Override
  public void readExternal(@NotNull final RunConfigurationBase runConfiguration, @NotNull Element element) throws InvalidDataException {
     if (!isApplicableFor(runConfiguration)) {
      return;
    }

    Objects.requireNonNull(JavaCoverageEnabledConfiguration.getFrom(runConfiguration)).readExternal(element);
  }

  @Override
  public void writeExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) {
    if (!isApplicableFor(runConfiguration)) {
      return;
    }
    Objects.requireNonNull(JavaCoverageEnabledConfiguration.getFrom(runConfiguration)).writeExternal(element);
  }

  @Override
  public void extendCreatedConfiguration(@NotNull RunConfigurationBase runJavaConfiguration, @NotNull Location location) {
    final JavaCoverageEnabledConfiguration coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(runJavaConfiguration);
    assert coverageEnabledConfiguration != null;
    if (runJavaConfiguration instanceof CommonJavaRunConfigurationParameters) {
      coverageEnabledConfiguration.setUpCoverageFilters(((CommonJavaRunConfigurationParameters)runJavaConfiguration).getRunClass(),
                                                        ((CommonJavaRunConfigurationParameters)runJavaConfiguration).getPackage());
    }
  }

  @Override
  public void cleanUserData(RunConfigurationBase runConfiguration) {
     runConfiguration.putCopyableUserData(CoverageEnabledConfiguration.COVERAGE_KEY, null);
  }

  @Override
  public RefactoringElementListener wrapElementListener(PsiElement element,
                                                        RunConfigurationBase configuration,
                                                        RefactoringElementListener listener) {
    if (!isApplicableFor(configuration)) {
      return listener;
    }
    final JavaCoverageEnabledConfiguration coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(configuration);
    if (coverageEnabledConfiguration != null) {
      final Project project = configuration.getProject();
      final ClassFilter[] patterns = coverageEnabledConfiguration.getCoveragePatterns();
      final String[] filters = getFilters(coverageEnabledConfiguration);
      if (patterns != null) {
        assert filters != null;
        if (element instanceof PsiClass) {
          final String qualifiedName = ((PsiClass)element).getQualifiedName();
          final int idx = ArrayUtil.find(filters, qualifiedName);
          if (idx > -1) {
            final RefactoringListeners.Accessor<PsiClass> accessor = new MyClassAccessor(project, patterns, idx, filters);
            final RefactoringElementListener classListener = RefactoringListeners.getClassOrPackageListener(element, accessor);
            if (classListener != null) {
              listener = appendListener(listener, classListener);
            }
          }
          else if (qualifiedName != null){
            final String packageName = StringUtil.getPackageName(qualifiedName);
            if (!StringUtil.isEmpty(packageName)) {
              int i = ArrayUtil.indexOf(filters, packageName + ".*");
              if (i != -1) {
                listener = appendListener(listener,
                                          new RefactoringListeners.RefactorPackageByClass(new MyClassAccessor(project, patterns, i, filters)));
              }
            }
          }
        } else if (element instanceof PsiPackage) {
          final String qualifiedName = ((PsiPackage)element).getQualifiedName();
          for (int i = 0, filtersLength = filters.length; i < filtersLength; i++) {
            if (filters[i].startsWith(qualifiedName + ".")) {
              final RefactoringElementListener packageListener;
              if (filters[i].endsWith("*")) {
                packageListener = RefactoringListeners.getListener((PsiPackage)element,
                                                                   new MyPackageAccessor(project, patterns, i, filters));
              } else {
                packageListener = RefactoringListeners.getClassOrPackageListener(element,
                                                                                 new MyClassAccessor(project, patterns, i, filters));
              }
              if (packageListener != null) {
                listener = appendListener(listener, packageListener);
              }
            }
          }
        }
      }
    }
    return listener;
  }

  private static String @Nullable [] getFilters(JavaCoverageEnabledConfiguration coverageEnabledConfiguration) {
    final ClassFilter[] patterns = coverageEnabledConfiguration.getCoveragePatterns();
    if (patterns != null) {
      final List<String> filters = new ArrayList<>();
      for (ClassFilter classFilter : patterns) {
        filters.add(classFilter.getPattern());
      }
      return ArrayUtilRt.toStringArray(filters);
    }
    return null;
  }

  private static RefactoringElementListener appendListener(RefactoringElementListener listener,
                                                           final RefactoringElementListener classOrPackageListener) {
    if (listener == null) {
      listener = new RefactoringElementListenerComposite();
    } else if (!(listener instanceof RefactoringElementListenerComposite)) {
      final RefactoringElementListenerComposite composite = new RefactoringElementListenerComposite();
      composite.addListener(listener);
      listener = composite;
    }
    ((RefactoringElementListenerComposite)listener).addListener(classOrPackageListener);
    return listener;
  }

  @Override
  public boolean isListenerDisabled(RunConfigurationBase configuration, Object listener, RunnerSettings runnerSettings) {
    if (listener instanceof CoverageListener) {
      if (!(runnerSettings instanceof CoverageRunnerData)) return true;
      final CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.getOrCreate(configuration);
      return !(coverageEnabledConfiguration.getCoverageRunner() instanceof IDEACoverageRunner) ||
             !(coverageEnabledConfiguration.isTrackPerTestCoverage() && coverageEnabledConfiguration.isTracingEnabled());
    }
    return false;
  }

  @Override
  public boolean isApplicableFor(@NotNull final RunConfigurationBase configuration) {
    return CoverageEnabledConfiguration.isApplicableTo(configuration);
  }

  private static final class MyPackageAccessor extends MyAccessor implements RefactoringListeners.Accessor<PsiPackage> {


    private MyPackageAccessor(Project project, ClassFilter[] patterns, int idx, String[] filters) {
      super(project, patterns, idx, filters);
    }

    @Override
    public void setName(String qualifiedName) {
      super.setName(qualifiedName + ".*");
    }

    @Override
    public PsiPackage getPsiElement() {
      final String name = getName();
      return JavaPsiFacade.getInstance(getProject()).findPackage(name.substring(0, name.length() - ".*".length()));
    }

    @Override
    public void setPsiElement(PsiPackage psiElement) {
      setName(psiElement.getQualifiedName());
    }
  }

  private static final class MyClassAccessor extends MyAccessor implements RefactoringListeners.Accessor<PsiClass> {

    private MyClassAccessor(Project project, ClassFilter[] patterns, int idx, String[] filters) {
      super(project, patterns, idx, filters);
    }

    @Override
    public PsiClass getPsiElement() {
      return JavaPsiFacade.getInstance(getProject()).findClass(getName(), GlobalSearchScope.allScope(getProject()));
    }

    @Override
    public void setPsiElement(PsiClass psiElement) {
      setName(psiElement.getQualifiedName());
    }
  }

  private static class MyAccessor {
    private final Project myProject;
    private final ClassFilter[] myPatterns;
    private final int myIdx;
    private final String[] myFilters;

    private MyAccessor(Project project, ClassFilter[] patterns, int idx, String[] filters) {
      myProject = project;
      myPatterns = patterns;
      myIdx = idx;
      myFilters = filters;
    }

    public void setName(String qName) {
      myPatterns[myIdx] = new ClassFilter(qName);
    }

    public String getName() {
      return myFilters[myIdx];
    }

    public Project getProject() {
      return myProject;
    }
  }
}
