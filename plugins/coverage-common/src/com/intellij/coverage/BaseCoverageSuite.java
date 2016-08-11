package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.coverage.data.ProjectData;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.ref.SoftReference;

/**
 * @author ven
 */
public abstract class BaseCoverageSuite  implements CoverageSuite, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(BaseCoverageSuite.class.getName());

  @NonNls
  private static final String FILE_PATH = "FILE_PATH";

  @NonNls
  private static final String SOURCE_PROVIDER = "SOURCE_PROVIDER";

  @NonNls
  private static final String MODIFIED_STAMP = "MODIFIED";

  @NonNls
  private static final String NAME_ATTRIBUTE = "NAME";

  @NonNls
  private static final String COVERAGE_RUNNER = "RUNNER";

  @NonNls
  private static final String COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME = "COVERAGE_BY_TEST_ENABLED";

  @NonNls
  private static final String TRACING_ENABLED_ATTRIBUTE_NAME = "COVERAGE_TRACING_ENABLED";

  private SoftReference<ProjectData> myCoverageData = new SoftReference<>(null);

  private String myName;
  private long myLastCoverageTimeStamp;
  private boolean myCoverageByTestEnabled;
  private CoverageRunner myRunner;
  private CoverageFileProvider myCoverageDataFileProvider;
  private boolean myTrackTestFolders;
  private boolean myTracingEnabled;
  private Project myProject;
  
  private RunConfigurationBase myConfiguration;

  protected BaseCoverageSuite() {
  }

  public BaseCoverageSuite(final String name,
                           @Nullable final CoverageFileProvider fileProvider,
                           final long lastCoverageTimeStamp,
                           final boolean coverageByTestEnabled,
                           final boolean tracingEnabled,
                           final boolean trackTestFolders,
                           final CoverageRunner coverageRunner) {
    this(name, fileProvider, lastCoverageTimeStamp, coverageByTestEnabled, tracingEnabled, trackTestFolders, coverageRunner, null);
  }

  public BaseCoverageSuite(final String name,
                           @Nullable final CoverageFileProvider fileProvider,
                           final long lastCoverageTimeStamp,
                           final boolean coverageByTestEnabled,
                           final boolean tracingEnabled,
                           final boolean trackTestFolders,
                           final CoverageRunner coverageRunner,
                           Project project) {
    myCoverageDataFileProvider = fileProvider;
    myName = name;
    myLastCoverageTimeStamp = lastCoverageTimeStamp;
    myCoverageByTestEnabled = coverageByTestEnabled;
    myTrackTestFolders = trackTestFolders;
    myTracingEnabled = tracingEnabled;
    myRunner = coverageRunner;
    myProject = project;
  }

  @Nullable
  public static CoverageRunner readRunnerAttribute(Element element) {
    final String runner = element.getAttributeValue(COVERAGE_RUNNER);
    if (runner != null) {
      for (CoverageRunner coverageRunner : Extensions.getExtensions(CoverageRunner.EP_NAME)) {
        if (Comparing.strEqual(coverageRunner.getId(), runner)) {
          return coverageRunner;
        }
      }
    }
    return null;
  }

  public static CoverageFileProvider readDataFileProviderAttribute(Element element) {
    final String sourceProvider = element.getAttributeValue(SOURCE_PROVIDER);
    final String relativePath = FileUtil.toSystemDependentName(element.getAttributeValue(FILE_PATH));
    final File file = new File(relativePath);
    return new DefaultCoverageFileProvider(file.exists() ? file
                                                         : new File(PathManager.getSystemPath(), relativePath),
                                            sourceProvider != null ? sourceProvider
                                                                   : DefaultCoverageFileProvider.class.getName());
  }

  public boolean isValid() {
    return myCoverageDataFileProvider.isValid();
  }

  @NotNull
  public String getCoverageDataFileName() {
    return myCoverageDataFileProvider.getCoverageDataFilePath();
  }

  public
  @NotNull
  CoverageFileProvider getCoverageDataFileProvider() {
    return myCoverageDataFileProvider;
  }

  public String getPresentableName() {
    return myName;
  }

  public long getLastCoverageTimeStamp() {
    return myLastCoverageTimeStamp;
  }

  public boolean isTrackTestFolders() {
    return myTrackTestFolders;
  }

  public boolean isTracingEnabled() {
    return myTracingEnabled;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myCoverageDataFileProvider = readDataFileProviderAttribute(element);

    // name
    myName = element.getAttributeValue(NAME_ATTRIBUTE);
    if (myName == null) myName = generateName();

    // tc
    myLastCoverageTimeStamp = Long.parseLong(element.getAttributeValue(MODIFIED_STAMP));

    // runner
    myRunner = readRunnerAttribute(element);

    // coverage per test
    final String collectedLineInfo = element.getAttributeValue(COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME);
    myCoverageByTestEnabled = collectedLineInfo != null && Boolean.valueOf(collectedLineInfo).booleanValue();


    // tracing
    final String tracingEnabled = element.getAttributeValue(TRACING_ENABLED_ATTRIBUTE_NAME);
    myTracingEnabled = tracingEnabled != null && Boolean.valueOf(tracingEnabled).booleanValue();
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    final String fileName =
      FileUtil.getRelativePath(new File(PathManager.getSystemPath()), new File(myCoverageDataFileProvider.getCoverageDataFilePath()));
    element.setAttribute(FILE_PATH, fileName != null ? FileUtil.toSystemIndependentName(fileName) : myCoverageDataFileProvider.getCoverageDataFilePath());
    element.setAttribute(NAME_ATTRIBUTE, myName);
    element.setAttribute(MODIFIED_STAMP, String.valueOf(myLastCoverageTimeStamp));
    element.setAttribute(SOURCE_PROVIDER, myCoverageDataFileProvider instanceof DefaultCoverageFileProvider
                                          ? ((DefaultCoverageFileProvider)myCoverageDataFileProvider).getSourceProvider()
                                          : myCoverageDataFileProvider.getClass().getName());
    // runner
    if (getRunner() != null) {
      element.setAttribute(COVERAGE_RUNNER, myRunner.getId());
    }

    // cover by test
    element.setAttribute(COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME, String.valueOf(myCoverageByTestEnabled));

    // tracing
    element.setAttribute(TRACING_ENABLED_ATTRIBUTE_NAME, String.valueOf(myTracingEnabled));
  }

  public void setCoverageData(final ProjectData projectData) {
    myCoverageData = new SoftReference<>(projectData);
  }

  public ProjectData getCoverageData() {
    return myCoverageData.get();
  }

  public void restoreCoverageData() {
    setCoverageData(loadProjectInfo());
  }

  public boolean isCoverageByTestApplicable() {
    return getRunner().isCoverageByTestApplicable();
  }

  public boolean isCoverageByTestEnabled() {
    return myCoverageByTestEnabled;
  }

  @Nullable
  public ProjectData getCoverageData(final CoverageDataManager coverageDataManager) {
    final ProjectData data = getCoverageData();
    if (data != null) {
      return data;
    }
    ProjectData map = loadProjectInfo();
    setCoverageData(map);
    return map;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final String thisName = myCoverageDataFileProvider.getCoverageDataFilePath();
    final String thatName = ((BaseCoverageSuite)o).myCoverageDataFileProvider.getCoverageDataFilePath();
    return thisName.equals(thatName);
  }

  public int hashCode() {
    return myCoverageDataFileProvider.getCoverageDataFilePath().hashCode();
  }

  @Nullable
  protected ProjectData loadProjectInfo() {
    String sessionDataFileName = getCoverageDataFileName();
    File sessionDataFile = new File(sessionDataFileName);
    if (!sessionDataFile.exists()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Nonexistent file given +" + sessionDataFileName);
      }
      return null;
    }
    return myRunner.loadCoverageData(sessionDataFile, this);
  }

  public CoverageRunner getRunner() {
    return myRunner;
  }

  protected void setRunner(CoverageRunner runner) {
    myRunner = runner;
  }

  private String generateName() {
    String text = myCoverageDataFileProvider.getCoverageDataFilePath();
    int i = text.lastIndexOf(File.separatorChar);
    if (i >= 0) text = text.substring(i + 1);
    i = text.lastIndexOf('.');
    if (i >= 0) text = text.substring(0, i);
    return text;
  }

  public Project getProject() {
    return myProject;
  }

  public void setConfiguration(RunConfigurationBase configuration) {
    myConfiguration = configuration;
  }

  @Nullable
  public RunConfigurationBase getConfiguration() {
    return myConfiguration;
  }
}
