// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.coverage.analysis.Annotator;
import com.intellij.coverage.analysis.JavaCoverageClassesAnnotator;
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
    PackageAnnotator.ClassCoverageInfo barClassCoverage = consumer.myClassCoverageInfo.get("foo.bar.BarClass");
    assertEquals(3, barClassCoverage.totalMethodCount);
    assertEquals(1, barClassCoverage.coveredMethodCount);
    PackageAnnotator.PackageCoverageInfo barPackageCoverage = consumer.myPackageCoverage.get("foo.bar");
    assertEquals(2, barPackageCoverage.coveredLineCount);
    assertEquals(8, barPackageCoverage.totalLineCount);
    assertEquals(1, barPackageCoverage.coveredMethodCount);
    assertEquals(7, barPackageCoverage.totalMethodCount);
    PackageAnnotator.ClassCoverageInfo uncoveredClassInfo = consumer.myClassCoverageInfo.get("foo.bar.UncoveredClass");
    assertEquals(4, uncoveredClassInfo.totalMethodCount);
    assertEquals(0, uncoveredClassInfo.coveredMethodCount);
  }

  public void testSingleClassFilter() {
    String[] filters = new String[]{"foo.bar.BarClass"};
    CoverageSuitesBundle bundle = loadCoverageSuite(IDEACoverageRunner.class, "simple$foo_in_simple.coverage", filters);
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    new JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite();

    assertEquals(1, consumer.myClassCoverageInfo.size());
    PackageAnnotator.ClassCoverageInfo barClassCoverage = consumer.myClassCoverageInfo.get("foo.bar.BarClass");
    assertEquals(3, barClassCoverage.totalMethodCount);
    assertEquals(1, barClassCoverage.coveredMethodCount);
  }

  public void testJaCoCo() {
    CoverageSuitesBundle bundle = loadCoverageSuite(JaCoCoCoverageRunner.class, "simple$foo_in_simple.jacoco.coverage", null);
    ClassData classData = bundle.getCoverageData().getClassData("foo.FooClass");
    // getStatus() never returns full coverage; it can only distinguish between none and partial
    assertEquals(LineCoverage.PARTIAL, classData.getStatus("method1()I").intValue());
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
}

