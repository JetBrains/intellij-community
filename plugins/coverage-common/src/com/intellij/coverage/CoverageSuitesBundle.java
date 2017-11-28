package com.intellij.coverage;

import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.reference.SoftReference;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

public class CoverageSuitesBundle {
  private final CoverageSuite[] mySuites;
  private CoverageEngine myEngine;

  private Set<Module> myProcessedModules;

  private CachedValue<GlobalSearchScope> myCachedValue;

  private SoftReference<ProjectData> myData = new SoftReference<>(null);
  private static final Logger LOG = Logger.getInstance(CoverageSuitesBundle.class);

  public CoverageSuitesBundle(CoverageSuite suite) {
    this(new CoverageSuite[]{suite});
  }

  public CoverageSuitesBundle(CoverageSuite[] suites) {
    mySuites = suites;
    LOG.assertTrue(mySuites.length > 0);
    myEngine = mySuites[0].getCoverageEngine();
    for (CoverageSuite suite : suites) {
      final CoverageEngine engine = suite.getCoverageEngine();
      LOG.assertTrue(Comparing.equal(engine, myEngine));
    }
  }


  public boolean isValid() {
    for (CoverageSuite suite : mySuites) {
      if (!suite.isValid()) return false;
    }
    return true;
  }

  public Project getProject() {
    return mySuites[0].getProject();
  }

  public long getLastCoverageTimeStamp() {
    long max = 0;
    for (CoverageSuite suite : mySuites) {
      max = Math.max(max, suite.getLastCoverageTimeStamp());
    }
    return max;
  }

  public boolean isCoverageByTestApplicable() {
    for (CoverageSuite suite : mySuites) {
      if (suite.isCoverageByTestApplicable()) return true;
    }
    return false;
  }

  public boolean isCoverageByTestEnabled() {
    for (CoverageSuite suite : mySuites) {
      if (suite.isCoverageByTestEnabled()) return true;
    }
    return false;
  }

  @Nullable
  public ProjectData getCoverageData() {
    final ProjectData projectData = myData.get();
    if (projectData != null) return projectData;
    ProjectData data = new ProjectData();
    for (CoverageSuite suite : mySuites) {
      final ProjectData coverageData = suite.getCoverageData(null);
      if (coverageData != null) {
        data.merge(coverageData);
      }
    }
    myData = new SoftReference<>(data);
    return data;
  }

  public boolean isTrackTestFolders() {
    for (CoverageSuite suite : mySuites) {
      if (suite.isTrackTestFolders()) return true;
    }
    return false;
  }

  public boolean isTracingEnabled() {
    for (CoverageSuite suite : mySuites) {
      if (suite.isTracingEnabled()) return true;
    }
    return false;
  }

  @NotNull
  public CoverageEngine getCoverageEngine() {
    return myEngine;
  }

  public LineMarkerRendererWithErrorStripe getLineMarkerRenderer(int lineNumber,
                                                                 @Nullable final String className,
                                                                 final TreeMap<Integer, LineData> lines,
                                                                 final boolean coverageByTestApplicable,
                                                                 @NotNull final CoverageSuitesBundle coverageSuite,
                                                                 final Function<Integer, Integer> newToOldConverter,
                                                                 final Function<Integer, Integer> oldToNewConverter, boolean subCoverageActive) {
    return myEngine.getLineMarkerRenderer(lineNumber, className, lines, coverageByTestApplicable, coverageSuite, newToOldConverter, oldToNewConverter, subCoverageActive);
  }

  public CoverageAnnotator getAnnotator(Project project) {
    return myEngine.getCoverageAnnotator(project);
  }

  @NotNull
  public CoverageSuite[] getSuites() {
    return mySuites;
  }

  public boolean contains(CoverageSuite suite) {
    return ArrayUtilRt.find(mySuites, suite) > -1;
  }

  public void setCoverageData(ProjectData projectData) {
    myData = new SoftReference<>(projectData);
  }

  public void restoreCoverageData() {
    myData = new SoftReference<>(null);
  }

  public String getPresentableName() {
    return StringUtil.join(mySuites, coverageSuite -> coverageSuite.getPresentableName(), ", ");
  }

  public boolean isModuleChecked(final Module module) {
    return myProcessedModules != null && myProcessedModules.contains(module);
  }

  public void checkModule(final Module module) {
    if (myProcessedModules == null) {
      myProcessedModules = new HashSet<>();
    }
    myProcessedModules.add(module);
  }

  @Nullable
  public RunConfigurationBase getRunConfiguration() {
    for (CoverageSuite suite : mySuites) {
      if (suite instanceof BaseCoverageSuite) {
        final RunConfigurationBase configuration = ((BaseCoverageSuite)suite).getConfiguration();
        if (configuration != null) {
          return configuration;
        }
      }
    }
    return null;
  }

  public GlobalSearchScope getSearchScope(final Project project) {
    if (myCachedValue == null) {
      myCachedValue = CachedValuesManager.getManager(project).createCachedValue(
        () -> new CachedValueProvider.Result<>(getSearchScopeInner(project), ProjectRootModificationTracker.getInstance(project)), false);
    }
    return myCachedValue.getValue();
    
  }

  private GlobalSearchScope getSearchScopeInner(Project project) {
    Module[] modules = Arrays.stream(mySuites).filter(suite -> suite instanceof BaseCoverageSuite)
      .map(suite -> ((BaseCoverageSuite)suite).getConfiguration())
      .filter(configuration -> configuration instanceof ModuleBasedConfiguration)
      .map(configuration -> ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule())
      .toArray(Module[]::new);

    if (modules.length == 0 || ArrayUtil.find(modules, null) > -1) {
      return isTrackTestFolders() ? GlobalSearchScope.projectScope(project) : GlobalSearchScopesCore.projectProductionScope(project);
    }

    return GlobalSearchScope.union(Arrays.stream(modules).map(module -> GlobalSearchScope.moduleRuntimeScope(module, isTrackTestFolders())).toArray(GlobalSearchScope[]::new));
  }
}
