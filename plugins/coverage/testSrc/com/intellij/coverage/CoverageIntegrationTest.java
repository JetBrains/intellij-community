// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.coverage.analysis.Annotator;
import com.intellij.coverage.analysis.JavaCoverageClassesAnnotator;
import com.intellij.coverage.analysis.JavaCoverageReportEnumerator;
import com.intellij.coverage.analysis.PackageAnnotator;
import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@ExcludeFromTestDiscovery
public class CoverageIntegrationTest extends JavaModuleTestCase {
  private static String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("coverage") + "/testData/simple";
  }

  @Override
  protected void setUpProject() {
    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(getTestDataPath()), getTestRootDisposable());
  }

  public void testIntegrationSimple() {
    CoverageSuitesBundle bundle = loadCoverageSuite(IDEACoverageRunner.class, "simple$foo_in_simple.coverage", null);
    CoverageDataManager.getInstance(myProject).chooseSuitesBundle(bundle);
  }

  public void testSimple() {
    CoverageSuitesBundle bundle = loadCoverageSuite(IDEACoverageRunner.class, "simple$foo_in_simple.coverage", null);
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    new JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite();

    assertEquals(3, consumer.myClassCoverageInfo.size());
    assertEquals(2, consumer.myFlatPackageCoverage.size());
    assertEquals(3, consumer.myPackageCoverage.size());
    assertEquals(3, consumer.myDirectoryCoverage.size());

    assertHits(consumer.myClassCoverageInfo.get("foo.bar.BarClass"), new int[]{1, 1, 3, 1, 4, 2, 0, 0});
    assertHits(consumer.myClassCoverageInfo.get("foo.bar.UncoveredClass"), new int[]{1, 0, 4, 0, 4, 0, 0, 0});
    assertHits(consumer.myClassCoverageInfo.get("foo.FooClass"), new int[]{1, 1, 2, 2, 3, 3, 0, 0});

    assertHits(consumer.myFlatPackageCoverage.get("foo.bar"), new int[]{2, 1, 7, 1, 8, 2, 0, 0});
    assertHits(consumer.myFlatPackageCoverage.get("foo"), new int[]{1, 1, 2, 2, 3, 3, 0, 0});

    assertHits(consumer.myPackageCoverage.get("foo.bar"), new int[]{2, 1, 7, 1, 8, 2, 0, 0});
    assertHits(consumer.myPackageCoverage.get("foo"), new int[]{3, 2, 9, 3, 11, 5, 0, 0});
    assertHits(consumer.myPackageCoverage.get(""), new int[]{3, 2, 9, 3, 11, 5, 0, 0});
  }

  public void testSingleClassFilter() {
    String[] filters = new String[]{"foo.bar.BarClass"};
    CoverageSuitesBundle bundle = loadCoverageSuite(IDEACoverageRunner.class, "simple$foo_in_simple.coverage", filters);
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    new JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite();

    assertEquals(1, consumer.myClassCoverageInfo.size());
    assertEquals(1, consumer.myFlatPackageCoverage.size());
    assertEquals(3, consumer.myPackageCoverage.size());
    assertEquals(1, consumer.myDirectoryCoverage.size());
    assertHits(consumer.myClassCoverageInfo.get("foo.bar.BarClass"), new int[]{1, 1, 3, 1, 4, 2, 0, 0});
    assertHits(consumer.myFlatPackageCoverage.get("foo.bar"), new int[]{1, 1, 3, 1, 4, 2, 0, 0});
    assertHits(consumer.myPackageCoverage.get("foo.bar"), new int[]{1, 1, 3, 1, 4, 2, 0, 0});
    assertHits(consumer.myPackageCoverage.get("foo"), new int[]{1, 1, 3, 1, 4, 2, 0, 0});
    assertHits(consumer.myPackageCoverage.get(""), new int[]{1, 1, 3, 1, 4, 2, 0, 0});
  }

  public void testJaCoCoProjectData() {
    CoverageSuitesBundle bundle = loadCoverageSuite(JaCoCoCoverageRunner.class, "simple$foo_in_simple.jacoco.coverage", null);
    ClassData classData = bundle.getCoverageData().getClassData("foo.FooClass");
    // getStatus() never returns full coverage; it can only distinguish between none and partial
    assertEquals(LineCoverage.PARTIAL, classData.getStatus("method1()I").intValue());
  }

  public void testJaCoCo() {
    CoverageSuitesBundle bundle = loadCoverageSuite(JaCoCoCoverageRunner.class, "simple$foo_in_simple.jacoco.coverage", null);
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    new JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite();
    assertJaCoCoHits(consumer);
  }

  public void testJaCoCoWithoutUnloaded() {
    CoverageSuitesBundle bundle = loadCoverageSuite(JaCoCoCoverageRunner.class, "simple$foo_in_simple.jacoco.coverage", null);
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    JavaCoverageReportEnumerator.collectSummaryInReport(bundle, myProject, consumer);
    assertJaCoCoHits(consumer);
  }

  private static void assertJaCoCoHits(PackageAnnotationConsumer consumer) {
    assertEquals(3, consumer.myClassCoverageInfo.size());
    assertEquals(2, consumer.myFlatPackageCoverage.size());
    assertEquals(3, consumer.myPackageCoverage.size());
    assertEquals(3, consumer.myDirectoryCoverage.size());

    assertHits(consumer.myClassCoverageInfo.get("foo.bar.BarClass"), new int[]{1, 1, 3, 1, 4, 2, 0, 0});
    assertHits(consumer.myClassCoverageInfo.get("foo.bar.UncoveredClass"), new int[]{1, 0, 4, 0, 4, 0, 0, 0});
    assertHits(consumer.myClassCoverageInfo.get("foo.FooClass"), new int[]{1, 1, 2, 2, 3, 3, 2, 1});

    assertHits(consumer.myFlatPackageCoverage.get("foo.bar"), new int[]{2, 1, 7, 1, 8, 2, 0, 0});
    assertHits(consumer.myFlatPackageCoverage.get("foo"), new int[]{1, 1, 2, 2, 3, 3, 2, 1});

    assertHits(consumer.myPackageCoverage.get("foo.bar"), new int[]{2, 1, 7, 1, 8, 2, 0, 0});
    assertHits(consumer.myPackageCoverage.get("foo"), new int[]{3, 2, 9, 3, 11, 5, 2, 1});
    assertHits(consumer.myPackageCoverage.get(""), new int[]{3, 2, 9, 3, 11, 5, 2, 1});
  }

  public void testHTMLReport() throws IOException {
    CoverageSuitesBundle bundle = loadCoverageSuite(IDEACoverageRunner.class, "simple$foo_in_simple.coverage", null);
    File htmlDir = Files.createTempDirectory("html").toFile();
    try {
      ExportToHTMLSettings.getInstance(myProject).OUTPUT_DIRECTORY = htmlDir.getAbsolutePath();
      new IDEACoverageRunner().generateReport(bundle, myProject);
      assertTrue(htmlDir.exists());
      assertTrue(new File(htmlDir, "index.html").exists());
    }
    finally {
      htmlDir.delete();
    }
  }

  private CoverageSuitesBundle loadCoverageSuite(Class<? extends CoverageRunner> coverageRunnerClass, String coverageDataPath,
                                                 String[] includeFilters) {
    File coverageFile = new File(getTestDataPath(), coverageDataPath);
    CoverageRunner runner = CoverageRunner.getInstance(coverageRunnerClass);
    CoverageFileProvider fileProvider = new DefaultCoverageFileProvider(coverageFile);
    CoverageSuite suite = JavaCoverageEngine.getInstance().createSuite(
      runner, "Simple", fileProvider, includeFilters, null,
      -1, false, false, false, myProject);
    return new CoverageSuitesBundle(suite);
  }

  private static class PackageAnnotationConsumer implements Annotator {
    private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirectoryCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlatPackageCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfo = new HashMap<>();

    @Override
    public void annotateSourceDirectory(VirtualFile virtualFile, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
      myDirectoryCoverage.put(virtualFile, packageCoverageInfo);
    }

    @Override
    public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
      myPackageCoverage.put(packageQualifiedName, packageCoverageInfo);
    }

    @Override
    public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo, boolean flatten) {
      (flatten ? myFlatPackageCoverage : myPackageCoverage).put(packageQualifiedName, packageCoverageInfo);
    }

    @Override
    public void annotateClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
      myClassCoverageInfo.put(classQualifiedName, classCoverageInfo);
    }
  }

  private static void assertHits(PackageAnnotator.SummaryCoverageInfo barClassCoverage, int[] hits) {
    assertEquals(hits[0], barClassCoverage.totalClassCount);
    assertEquals(hits[1], barClassCoverage.coveredClassCount);
    assertEquals(hits[2], barClassCoverage.totalMethodCount);
    assertEquals(hits[3], barClassCoverage.coveredMethodCount);
    assertEquals(hits[4], barClassCoverage.totalLineCount);
    assertEquals(hits[5], barClassCoverage.getCoveredLineCount());
    assertEquals(hits[6], barClassCoverage.totalBranchCount);
    assertEquals(hits[7], barClassCoverage.coveredBranchCount);
  }
}

