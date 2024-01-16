// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

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
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Contains array of suites which should have the same {@link CoverageEngine}.
 */
public class CoverageSuitesBundle {
  private static final Logger LOG = Logger.getInstance(CoverageSuitesBundle.class);
  private final CoverageSuite[] mySuites;
  private final CoverageEngine myEngine;

  private Set<Module> myProcessedModules;

  private CachedValue<GlobalSearchScope> myCachedValue;

  private SoftReference<ProjectData> myData = new SoftReference<>(null);
  private boolean myShouldActivateToolWindow = true;

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
    ProjectData projectData = myData.get();
    if (projectData != null) return projectData;

    List<ProjectData> dataList = Arrays.stream(mySuites)
      .map(suite -> suite.getCoverageData(null))
      .filter(data -> data != null)
      .toList();

    ProjectData data;
    if (dataList.size() == 1) {
      data = dataList.get(0);
    }
    else {
      data = new ProjectData();
      for (ProjectData coverageData : dataList) {
        data.merge(coverageData);
      }
      data.setIncludePatterns(mergeIncludeFilters(dataList));
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

  public boolean isBranchCoverage() {
    for (CoverageSuite suite : mySuites) {
      if (suite.isBranchCoverage()) return true;
    }
    return false;
  }

  @NotNull
  public CoverageEngine getCoverageEngine() {
    return myEngine;
  }

  public LineMarkerRendererWithErrorStripe getLineMarkerRenderer(int lineNumber,
                                                                 @Nullable final String className,
                                                                 @NotNull final TreeMap<Integer, LineData> lines,
                                                                 final boolean coverageByTestApplicable,
                                                                 @NotNull final CoverageSuitesBundle coverageSuite,
                                                                 final Function<? super Integer, Integer> newToOldConverter,
                                                                 final Function<? super Integer, Integer> oldToNewConverter, boolean subCoverageActive) {
    return myEngine.getLineMarkerRenderer(lineNumber, className, lines, coverageByTestApplicable, coverageSuite, newToOldConverter, oldToNewConverter, subCoverageActive);
  }

  public CoverageAnnotator getAnnotator(@NotNull Project project) {
    return myEngine.getCoverageAnnotator(project);
  }

  public CoverageSuite @NotNull [] getSuites() {
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
    for (CoverageSuite suite : mySuites) {
      suite.restoreCoverageData();
    }
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
    List<GlobalSearchScope> suiteScopes = Arrays.stream(mySuites).filter(suite -> suite instanceof BaseCoverageSuite)
      .map(suite -> ((BaseCoverageSuite)suite).getSearchScope(project))
      .filter(Objects::nonNull).toList();

    if (suiteScopes.size() != mySuites.length) {
      return isTrackTestFolders() ? GlobalSearchScope.projectScope(project) : GlobalSearchScopesCore.projectProductionScope(project);
    }
    return GlobalSearchScope.union(suiteScopes);
  }

  public boolean shouldActivateToolWindow() {
    return myShouldActivateToolWindow;
  }

  public void setShouldActivateToolWindow(boolean shouldActivateToolWindow) {
    myShouldActivateToolWindow = shouldActivateToolWindow;
  }

  boolean ensureReportFilesExist() {
    return ContainerUtil.and(mySuites, s -> s.getCoverageDataFileProvider().ensureFileExists());
  }

  /**
   * Merge include filters from different coverage report into one list.
   * @return merged list or <code>null</code> if some of the reports has empty include filters
   */
  private static @Nullable List<Pattern> mergeIncludeFilters(@NotNull List<ProjectData> dataList) {
    boolean hasEmptyFilters = false;
    Set<String> result = new HashSet<>();
    for (ProjectData data : dataList) {
      List<Pattern> patterns = data.getIncudePatterns();
      if (patterns == null || patterns.isEmpty()) {
        hasEmptyFilters = true;
      }
      else {
        result.addAll(ContainerUtil.map(patterns, Pattern::pattern));
      }
    }
    if (hasEmptyFilters) {
      if (!result.isEmpty()) {
        LOG.warn("CoverageSuitesBundle contains suites with filters impossible to merge. " +
                 "Please consider setting more precise filters for all suites. No filters applied.");
      }
      return null;
    }
    return ContainerUtil.map(result, Pattern::compile);
  }
}
