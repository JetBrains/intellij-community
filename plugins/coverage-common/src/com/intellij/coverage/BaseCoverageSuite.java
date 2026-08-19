// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.rt.coverage.data.ProjectData;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class BaseCoverageSuite implements CoverageSuite, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(BaseCoverageSuite.class.getName());

  private static final @NonNls String FILE_PATH = "FILE_PATH";
  private static final @NonNls String SOURCE_PROVIDER = "SOURCE_PROVIDER";
  private static final @NonNls String MODIFIED_STAMP = "MODIFIED";
  private static final @NonNls String NAME_ATTRIBUTE = "NAME";
  private static final @NonNls String COVERAGE_RUNNER = "RUNNER";
  private static final @NonNls String COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME = "COVERAGE_BY_TEST_ENABLED";
  private static final @NonNls String BRANCH_COVERAGE_ATTRIBUTE_NAME = "COVERAGE_TRACING_ENABLED";

  private SoftReference<ProjectData> myCoverageData = new SoftReference<>(null);

  private String myName;
  private Project myProject;
  @ApiStatus.Internal
  protected CoverageRunner myRunner;
  private CoverageFileProvider myCoverageDataFileProvider;
  private long myTimestamp;

  private RunConfigurationBase<?> myConfiguration;

  @ApiStatus.Internal
  protected boolean myTrackTestFolders = false;
  @ApiStatus.Internal
  protected boolean myBranchCoverage = false;
  @ApiStatus.Internal
  protected boolean myCoverageByTestEnabled = false;


  protected BaseCoverageSuite() { }

  public BaseCoverageSuite(@NotNull String name,
                           @Nullable Project project,
                           @Nullable CoverageRunner runner,
                           @Nullable CoverageFileProvider fileProvider,
                           long timestamp) {
    myName = name;
    myProject = project;
    myRunner = runner;
    myCoverageDataFileProvider = fileProvider;
    myTimestamp = timestamp;
  }

  /**
   * @deprecated Use {@link BaseCoverageSuite#BaseCoverageSuite(String, Project, CoverageRunner, CoverageFileProvider, long)}
   */
  @Deprecated
  public BaseCoverageSuite(String name,
                           @Nullable CoverageFileProvider fileProvider,
                           long timestamp,
                           boolean coverageByTestEnabled,
                           boolean branchCoverage,
                           boolean trackTestFolders,
                           CoverageRunner coverageRunner,
                           @Nullable Project project) {
    this(name, project, coverageRunner, fileProvider, timestamp);
    myTrackTestFolders = trackTestFolders;
    myBranchCoverage = branchCoverage;
    myCoverageByTestEnabled = coverageByTestEnabled;
  }

  @Override
  public boolean isValid() {
    return myCoverageDataFileProvider.isValid();
  }

  @Override
  public String getPresentableName() {
    return myName;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @ApiStatus.Internal
  public void setProject(Project project) {
    myProject = project;
  }

  @Override
  public CoverageRunner getRunner() {
    return myRunner;
  }

  @Override
  public @NotNull CoverageFileProvider getCoverageDataFileProvider() {
    return myCoverageDataFileProvider;
  }

  @Override
  public @NotNull String getCoverageDataFileName() {
    return myCoverageDataFileProvider.getCoverageDataFilePath();
  }

  @Override
  public long getLastCoverageTimeStamp() {
    return myTimestamp;
  }

  @Override
  public boolean isTrackTestFolders() {
    return myTrackTestFolders;
  }

  @Override
  public boolean isBranchCoverage() {
    return myBranchCoverage;
  }

  @ApiStatus.Internal
  @Override
  public boolean isCoverageByTestEnabled() {
    return myCoverageByTestEnabled;
  }

  public @Nullable RunConfigurationBase<?> getConfiguration() {
    return myConfiguration;
  }

  public void setConfiguration(RunConfigurationBase<?> configuration) {
    myConfiguration = configuration;
  }

  @ApiStatus.Internal
  public @NotNull @Unmodifiable List<Module> getRelatedModules(@NotNull Project project) {
    return getRelatedModules(getConfiguration(), project);
  }

  @ApiStatus.Internal
  public static @NotNull @Unmodifiable List<Module> getRelatedModules(@NotNull RunConfigurationBase<?> configuration) {
    return getRelatedModules(configuration, configuration.getProject());
  }

  @ApiStatus.Internal
  public static @NotNull @Unmodifiable List<Module> getRelatedModules(@Nullable RunConfigurationBase<?> configuration,
                                                                      @NotNull Project project) {
    Module module = getConfigurationModule(configuration);
    if (module != null) {
      LinkedHashSet<Module> modules = new LinkedHashSet<>();
      ReadAction.runBlocking(() -> ModuleUtilCore.getDependencies(module, modules));
      return List.copyOf(modules);
    }
    return List.of(ModuleManager.getInstance(project).getModules());
  }

  @Override
  public @Nullable ProjectData getCoverageData(final CoverageDataManager coverageDataManager) {
    ProjectData data = getCoverageData();
    if (data == null) {
      data = loadProjectInfo();
      setCoverageData(data);
    }
    return data;
  }

  /**
   * @return Cached coverage data without loading
   */
  public ProjectData getCoverageData() {
    return myCoverageData.get();
  }

  @Override
  public void setCoverageData(final ProjectData projectData) {
    myCoverageData = new SoftReference<>(projectData);
  }

  @ApiStatus.Internal
  @Override
  public void restoreCoverageData() {
    setCoverageData(loadProjectInfo());
  }

  protected @Nullable ProjectData loadProjectInfo() {
    String sessionDataFileName = myCoverageDataFileProvider.getCoverageDataFilePath();
    if (sessionDataFileName == null) return null;
    Path sessionDataFile = Path.of(sessionDataFileName);
    if (!Files.exists(sessionDataFile)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Nonexistent file given +" + sessionDataFileName);
      }
      return null;
    }
    final long startNs = System.nanoTime();
    final ProjectData projectData = myRunner.loadCoverageDataWithReporting(sessionDataFile, this);
    final long timeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    if (projectData != null) {
      CoverageLogger.logReportLoading(myProject, myRunner, timeMs, projectData.getClassesNumber());
    }
    return projectData;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myCoverageDataFileProvider = readDataFileProviderAttribute(element);

    // name
    myName = element.getAttributeValue(NAME_ATTRIBUTE);
    if (myName == null) {
      myName = generateName(myCoverageDataFileProvider.getCoverageDataFilePath());
    }

    // tc
    myTimestamp = Long.parseLong(element.getAttributeValue(MODIFIED_STAMP));

    // runner
    myRunner = readRunnerAttribute(element);

    // coverage per test
    final String collectedLineInfo = element.getAttributeValue(COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME);
    myCoverageByTestEnabled = collectedLineInfo != null && Boolean.valueOf(collectedLineInfo).booleanValue();


    // line/branch coverage
    final String branchCoverage = element.getAttributeValue(BRANCH_COVERAGE_ATTRIBUTE_NAME);
    myBranchCoverage = branchCoverage != null && Boolean.valueOf(branchCoverage).booleanValue();
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    String absolutePath = myCoverageDataFileProvider.getCoverageDataFilePath();
    String pathInSystemDir = FileUtil.getRelativePath(FileUtil.toSystemIndependentName(PathManager.getSystemPath()),
                                                      FileUtil.toSystemIndependentName(absolutePath), '/');
    element.setAttribute(FILE_PATH, pathInSystemDir != null ? FileUtil.toSystemIndependentName(pathInSystemDir) : absolutePath);
    element.setAttribute(NAME_ATTRIBUTE, myName);
    element.setAttribute(MODIFIED_STAMP, String.valueOf(myTimestamp));
    element.setAttribute(SOURCE_PROVIDER, myCoverageDataFileProvider instanceof DefaultCoverageFileProvider defaultProvider
                                          ? defaultProvider.getSourceProvider()
                                          : myCoverageDataFileProvider.getClass().getName());
    // runner
    if (myRunner != null) {
      element.setAttribute(COVERAGE_RUNNER, myRunner.getId());
    }

    // cover by test
    element.setAttribute(COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME, String.valueOf(myCoverageByTestEnabled));

    // line/branch coverage
    element.setAttribute(BRANCH_COVERAGE_ATTRIBUTE_NAME, String.valueOf(myBranchCoverage));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final String thisName = myCoverageDataFileProvider.getCoverageDataFilePath();
    final String thatName = ((BaseCoverageSuite)o).myCoverageDataFileProvider.getCoverageDataFilePath();
    return thisName.equals(thatName);
  }

  @Override
  public int hashCode() {
    return myCoverageDataFileProvider.getCoverageDataFilePath().hashCode();
  }

  @ApiStatus.Internal
  public GlobalSearchScope getSearchScope(Project project) {
    GlobalSearchScope scope = isTrackTestFolders() ? GlobalSearchScope.projectScope(project)
                                                   : GlobalSearchScopesCore.projectProductionScope(project);
    Module module = getConfigurationModule(getConfiguration());
    if (module != null) {
      return GlobalSearchScope.moduleWithDependenciesScope(module).intersectWith(scope);
    }
    return scope;
  }

  private static @Nullable Module getConfigurationModule(@Nullable RunConfigurationBase<?> configuration) {
    return configuration instanceof ModuleBasedConfiguration<?, ?> moduleConfiguration
           ? moduleConfiguration.getConfigurationModule().getModule()
           : null;
  }

  private static String generateName(String path) {
    Path file = NioFiles.toPath(path);
    String text = file != null ? NioFiles.getFileName(file) : path;
    int i = text.lastIndexOf('.');
    if (i >= 0) text = text.substring(0, i);
    return text;
  }

  static @Nullable CoverageRunner readRunnerAttribute(@NotNull Element element) {
    final String runner = element.getAttributeValue(COVERAGE_RUNNER);
    return runner == null ? null : CoverageRunner.getInstanceById(runner);
  }

  private static @NotNull CoverageFileProvider readDataFileProviderAttribute(Element element) {
    String sourceProvider = element.getAttributeValue(SOURCE_PROVIDER);
    if (sourceProvider == null) {
      sourceProvider = DefaultCoverageFileProvider.DEFAULT_LOCAL_PROVIDER_KEY;
    }

    String relativeOrAbsolutePath = FileUtil.toSystemDependentName(element.getAttributeValue(FILE_PATH));
    Path file = Path.of(relativeOrAbsolutePath);
    if (!file.isAbsolute() && !Files.exists(file)) {
      file = PathManager.getSystemDir().resolve(relativeOrAbsolutePath);
    }
    return new DefaultCoverageFileProvider(file, sourceProvider);
  }
}
