package com.intellij.coverage;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class CoverageSuiteImpl extends CoverageSuite implements JDOMExternalizable {
  private String myName;
  private @NotNull CoverageFileProvider myCoverageDataFileProvider;
  private String[] myFilters;
  private long myLastCoverageTimeStamp;
  private String mySuiteToMerge;
  private boolean myCoverageByTestEnabled;
  private CoverageRunner myRunner;

  private static final Logger LOG = Logger.getInstance("com.intellij.coverage.CoverageSuite");
  private SoftReference<ProjectData> myCoverageData = new SoftReference<ProjectData>(null);

  @NonNls
  private static final String FILE_PATH = "FILE_PATH";

  @NonNls
  private static final String SOURCE_PROVIDER = "SOURCE_PROVIDER";

  @NonNls
  private static final String MODIFIED_STAMP = "MODIFIED";

  @NonNls
  private static final String NAME_ATTRIBUTE = "NAME";

  @NonNls
  private static final String FILTER = "FILTER";
  @NonNls
  private static final String MERGE_SUITE = "MERGE_SUITE";
  @NonNls
  private static final String COVERAGE_RUNNER = "RUNNER";
  @NonNls private static final String COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME = "COVERAGE_BY_TEST_ENABLED";
  @NonNls private static final String TRACING_ENABLED_ATTRIBUTE_NAME = "COVERAGE_TRACING_ENABLED";
  private boolean myTracingEnabled;
  private boolean myTrackTestFolders;


  //read external only
  public CoverageSuiteImpl() {
  }

  public CoverageSuiteImpl(final String name,
                           final CoverageFileProvider coverageDataFileProvider,
                           final String[] filters,
                           final long lastCoverageTimeStamp,
                           final String suiteToMerge,
                           final boolean coverageByTestEnabled,
                           final boolean tracingEnabled,
                           final boolean trackTestFolders,
                           final CoverageRunner coverageRunner) {
    myName = name;
    myCoverageDataFileProvider = coverageDataFileProvider;
    myFilters = filters;
    myLastCoverageTimeStamp = lastCoverageTimeStamp;
    mySuiteToMerge = suiteToMerge;
    myCoverageByTestEnabled = coverageByTestEnabled;
    myTracingEnabled = tracingEnabled;
    myTrackTestFolders = trackTestFolders;
    myRunner = coverageRunner != null ? coverageRunner : CoverageRunner.getInstance(EmmaCoverageRunner.class);
  }

  public boolean isTrackTestFolders() {
    return myTrackTestFolders;
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

  @NotNull
  public String[] getFilteredPackageNames() {
    if (myFilters == null || myFilters.length == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<String>();
    for (String filter : myFilters) {
      if (filter.equals("*")) {
        result.add(""); //default package
      }
      else if (filter.endsWith(".*")) result.add(filter.substring(0, filter.length() - 2));
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  public String[] getFilteredClassNames() {
    if (myFilters == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<String>();
    for (String filter : myFilters) {
      if (!filter.equals("*") && !filter.endsWith(".*")) result.add(filter);
    }
    return ArrayUtil.toStringArray(result);
  }

  public long getLastCoverageTimeStamp() {
    return myLastCoverageTimeStamp;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final String thisName = myCoverageDataFileProvider.getCoverageDataFilePath();
    final String thatName = ((CoverageSuiteImpl)o).myCoverageDataFileProvider.getCoverageDataFilePath();
    return thisName.equals(thatName);
  }

  public int hashCode() {
    return myCoverageDataFileProvider.getCoverageDataFilePath().hashCode();
  }

  public String getPresentableName() {
    return myName;
  }

  public boolean isValid() {
    try {
      return myCoverageDataFileProvider.isValid();
    }
    catch (AbstractMethodError e) { //plugin runtime compatibility
      return true;
    }
  }

  private String generateName() {
    String text = myCoverageDataFileProvider.getCoverageDataFilePath();
    int i = text.lastIndexOf(File.separatorChar);
    if (i >= 0) text = text.substring(i + 1);
    i = text.lastIndexOf('.');
    if (i >= 0) text = text.substring(0, i);
    return text;
  }

  public void readExternal(Element element) throws InvalidDataException {
    final String sourceProvider = element.getAttributeValue(SOURCE_PROVIDER);
    final String relativePath = FileUtil.toSystemDependentName(element.getAttributeValue(FILE_PATH));
    final File file = new File(relativePath);
    myCoverageDataFileProvider = new DefaultCoverageFileProvider(file.exists() ? file : new File(PathManager.getSystemPath(), relativePath),
                                                                 sourceProvider != null ? sourceProvider : DefaultCoverageFileProvider.class.getName());
    myName = element.getAttributeValue(NAME_ATTRIBUTE);
    if (myName == null) myName = generateName();
    myLastCoverageTimeStamp = Long.parseLong(element.getAttributeValue(MODIFIED_STAMP));
    final List children = element.getChildren(FILTER);
    List<String> filters = new ArrayList<String>();
    //noinspection unchecked
    for (Element child : ((Iterable<Element>)children)) {
      filters.add(child.getValue());
    }
    myFilters = filters.isEmpty() ? null : ArrayUtil.toStringArray(filters);
    mySuiteToMerge = element.getAttributeValue(MERGE_SUITE);
    final String runner = element.getAttributeValue(COVERAGE_RUNNER);
    if (runner != null) {
      for (CoverageRunner coverageRunner : Extensions.getExtensions(CoverageRunner.EP_NAME)) {
        if (Comparing.strEqual(coverageRunner.getId(), runner)) {
          myRunner = coverageRunner;
          break;
        }
      }
    } else {
      myRunner = CoverageRunner.getInstance(EmmaCoverageRunner.class); //default
    }
    final String collectedLineInfo = element.getAttributeValue(COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME);
    myCoverageByTestEnabled = collectedLineInfo != null && Boolean.valueOf(collectedLineInfo).booleanValue();

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
    if (mySuiteToMerge != null) {
      element.setAttribute(MERGE_SUITE, mySuiteToMerge);
    }
    if (myFilters != null) {
      for (String filter : myFilters) {
        final Element filterElement = new Element(FILTER);
        filterElement.setText(filter);
        element.addContent(filterElement);
      }
    }
    element.setAttribute(COVERAGE_RUNNER, myRunner != null ? myRunner.getId() : "emma");
    element.setAttribute(COVERAGE_BY_TEST_ENABLED_ATTRIBUTE_NAME, String.valueOf(myCoverageByTestEnabled));
    element.setAttribute(TRACING_ENABLED_ATTRIBUTE_NAME, String.valueOf(myTracingEnabled));
  }

  @Nullable
  public ProjectData getCoverageData(final CoverageDataManager coverageDataManager) {
    final ProjectData data = myCoverageData.get();
    if (data != null) return data;
    ProjectData map = loadProjectInfo();
    if (mySuiteToMerge != null) {
      CoverageSuiteImpl toMerge = null;
      final CoverageSuite[] suites = coverageDataManager.getSuites();
      for (CoverageSuite suite : suites) {
        if (Comparing.strEqual(suite.getPresentableName(), mySuiteToMerge)) {
          if (!Comparing.strEqual(((CoverageSuiteImpl)suite).getSuiteToMerge(), getPresentableName())) {
            toMerge = (CoverageSuiteImpl)suite;
          }
          break;
        }
      }
      if (toMerge != null) {
        final ProjectData projectInfo = toMerge.getCoverageData(coverageDataManager);
        if (map != null) {
          map.merge(projectInfo);
        } else {
          map = projectInfo;
        }
      }
    }
    myCoverageData = new SoftReference<ProjectData>(map);
    return map;
  }

  @Nullable
  private ProjectData loadProjectInfo() {
    String sessionDataFileName = getCoverageDataFileName();
    File sessionDataFile = new File(sessionDataFileName);
    if (!sessionDataFile.exists()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Nonexistent file given +" + sessionDataFileName);
      }
      return null;
    }
    return myRunner.loadCoverageData(sessionDataFile);
  }

  public void setCoverageData(final ProjectData projectData) {
    myCoverageData = new SoftReference<ProjectData>(projectData);
  }

  public void restoreCoverageData() {
    myCoverageData = new SoftReference<ProjectData>(loadProjectInfo());
  }

  @Nullable
  public String getSuiteToMerge() {
    return mySuiteToMerge;
  }

  public boolean isClassFiltered(final String classFQName) {
    for (final String className : getFilteredClassNames()) {
      if (className.equals(classFQName) || classFQName.startsWith(className) && classFQName.charAt(className.length()) == '$') {
        return true;
      }
    }
    return false;
  }

  public boolean isPackageFiltered(final String packageFQName) {
    final String[] filteredPackageNames = getFilteredPackageNames();
    for (final String packName : filteredPackageNames) {
      if (packName.equals(packageFQName) || packageFQName.startsWith(packName) && packageFQName.charAt(packName.length()) == '.') {
        return true;
      }
    }
    return filteredPackageNames.length == 0;
  }

  public boolean isCoverageByTestApplicable() {
    return myRunner.isCoverageByTestApplicable();
  }

  public CoverageRunner getRunner() {
    return myRunner;
  }

  public boolean isCoverageByTestEnabled() {
    return myCoverageByTestEnabled;
  }

  public boolean isTracingEnabled() {
    return myTracingEnabled;
  }
}
