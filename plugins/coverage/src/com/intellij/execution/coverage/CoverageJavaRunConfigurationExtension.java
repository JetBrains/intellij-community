/*
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.execution.coverage;

import com.intellij.coverage.*;
import com.intellij.coverage.listeners.CoverageListener;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.Location;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.coverage.CoverageConfigurable;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers "Coverage" tab in Java run configurations
 */
public class CoverageJavaRunConfigurationExtension extends RunConfigurationExtension {
  public void attachToProcess(@NotNull final RunConfigurationBase configuration, @NotNull ProcessHandler handler, RunnerSettings runnerSettings) {
    CoverageDataManager.getInstance(configuration.getProject()).attachToProcess(handler, configuration, runnerSettings);
  }

  @Nullable
  public SettingsEditor createEditor(@NotNull RunConfigurationBase configuration) {
    return new CoverageConfigurable(configuration);
  }

  public String getEditorTitle() {
    return CoverageEngine.getEditorTitle();
  }

  @NotNull
  @Override
  public String getSerializationId() {
    return "coverage";
  }

  public void updateJavaParameters(RunConfigurationBase configuration, JavaParameters params, RunnerSettings runnerSettings) {
    if (!isApplicableFor(configuration)) {
      return;
    }

    final JavaCoverageEnabledConfiguration coverageConfig = JavaCoverageEnabledConfiguration.getFrom(configuration);
    //noinspection ConstantConditions
    coverageConfig.setCurrentCoverageSuite(null);
    final CoverageRunner coverageRunner = coverageConfig.getCoverageRunner();
    if (runnerSettings instanceof CoverageRunnerData && coverageRunner != null) {
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(configuration.getProject());
      coverageConfig.setCurrentCoverageSuite(coverageDataManager.addCoverageSuite(coverageConfig));
      coverageConfig.appendCoverageArgument(configuration, params);

      final Sdk jdk = params.getJdk();
      if (jdk != null && JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_7) && coverageRunner instanceof JavaCoverageRunner && !((JavaCoverageRunner)coverageRunner).isJdk7Compatible()) {
        Notifications.Bus.notify(new Notification("Coverage", "Coverage instrumentation is not fully compatible with JDK 7",
                                                  coverageRunner.getPresentableName() +
                                                  " coverage instrumentation can lead to java.lang.VerifyError errors with JDK 7. If so, please try IDEA coverage runner.",
                                                  NotificationType.WARNING));
      }
    }
  }

  @Override
  public void readExternal(@NotNull final RunConfigurationBase runConfiguration, @NotNull Element element) throws InvalidDataException {
     if (!isApplicableFor(runConfiguration)) {
      return;
    }

    //noinspection ConstantConditions
    JavaCoverageEnabledConfiguration.getFrom(runConfiguration).readExternal(element);
  }

  @Override
  public void writeExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) throws WriteExternalException {
    if (!isApplicableFor(runConfiguration)) {
      return;
    }
    //noinspection ConstantConditions
    JavaCoverageEnabledConfiguration.getFrom(runConfiguration).writeExternal(element);
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
  public void validateConfiguration(@NotNull RunConfigurationBase runJavaConfiguration, boolean isExecution)
    throws RuntimeConfigurationException {
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
              for (int i = 0; i < filters.length; i++) {
                String filter = filters[i];
                if (filter.equals(packageName + ".*")) {
                  listener = appendListener(listener,
                                            new RefactoringListeners.RefactorPackageByClass(new MyClassAccessor(project, patterns, i, filters)));
                  break;
                }
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

  @Nullable
  private static String[] getFilters(JavaCoverageEnabledConfiguration coverageEnabledConfiguration) {
    final ClassFilter[] patterns = coverageEnabledConfiguration.getCoveragePatterns();
    if (patterns != null) {
      final List<String> filters = new ArrayList<>();
      for (ClassFilter classFilter : patterns) {
        filters.add(classFilter.getPattern());
      }
      return ArrayUtil.toStringArray(filters);
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
             !(coverageEnabledConfiguration.isTrackPerTestCoverage() && !coverageEnabledConfiguration.isSampling());
    }
    return false;
  }

  protected boolean isApplicableFor(@NotNull final RunConfigurationBase configuration) {
    return CoverageEnabledConfiguration.isApplicableTo(configuration);
  }

  private static class MyPackageAccessor extends MyAccessor implements RefactoringListeners.Accessor<PsiPackage> {


    private MyPackageAccessor(Project project, ClassFilter[] patterns, int idx, String[] filters) {
      super(project, patterns, idx, filters);
    }

    public void setName(String qualifiedName) {
      super.setName(qualifiedName + ".*");
    }

    public PsiPackage getPsiElement() {
      final String name = getName();
      return JavaPsiFacade.getInstance(getProject()).findPackage(name.substring(0, name.length() - ".*".length()));
    }

    public void setPsiElement(PsiPackage psiElement) {
      setName(psiElement.getQualifiedName());
    }
  }

  private static class MyClassAccessor extends MyAccessor implements RefactoringListeners.Accessor<PsiClass> {

    private MyClassAccessor(Project project, ClassFilter[] patterns, int idx, String[] filters) {
      super(project, patterns, idx, filters);
    }

    public PsiClass getPsiElement() {
      return JavaPsiFacade.getInstance(getProject()).findClass(getName(), GlobalSearchScope.allScope(getProject()));
    }

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