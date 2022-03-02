// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
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

  public void testSimple() {
    CoverageSuitesBundle bundle = loadCoverageSuite(IDEACoverageRunner.class, "simple$foo_in_simple.coverage");
    PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage("foo");
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    new JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitRootPackage(psiPackage);
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

  public void testJaCoCo() {
    CoverageSuitesBundle bundle = loadCoverageSuite(JaCoCoCoverageRunner.class, "simple$foo_in_simple.jacoco.coverage");
    ClassData classData = bundle.getCoverageData().getClassData("foo.FooClass");
    // getStatus() never returns full coverage; it can only distinguish between none and partial
    assertEquals(LineCoverage.PARTIAL, classData.getStatus("method1()I").intValue());
  }

  public void testHTMLReport() throws IOException {
    CoverageSuitesBundle bundle = loadCoverageSuite(IDEACoverageRunner.class, "simple$foo_in_simple.coverage");
    File htmlDir = Files.createTempDirectory("html").toFile();
    try {
      ExportToHTMLSettings.getInstance(myProject).OUTPUT_DIRECTORY = htmlDir.getAbsolutePath();
      new IDEACoverageRunner().generateReport(bundle, myProject);
      assertTrue(htmlDir.exists());
      assertTrue(new File(htmlDir, "index.html").exists());
    } finally {
      htmlDir.delete();
    }
  }

  private CoverageSuitesBundle loadCoverageSuite(Class<? extends CoverageRunner> coverageRunnerClass, String coverageDataPath) {
    File coverageFile = new File(getTestDataPath(), coverageDataPath);
    CoverageRunner runner = CoverageRunner.getInstance(coverageRunnerClass);
    CoverageFileProvider fileProvider = new DefaultCoverageFileProvider(coverageFile);
    CoverageSuite suite =
      JavaCoverageEngine.getInstance().createCoverageSuite(runner, "Simple", fileProvider, null, -1, null, false, false, false, myProject);
    CoverageSuitesBundle bundle = new CoverageSuitesBundle(suite);
    CoverageDataManager.getInstance(myProject).chooseSuitesBundle(bundle);
    return bundle;
  }

  private static class PackageAnnotationConsumer implements PackageAnnotator.Annotator {
    private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirectoryCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlatPackageCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfo = new HashMap<>();

    @Override
    public void annotateSourceDirectory(VirtualFile virtualFile, PackageAnnotator.PackageCoverageInfo packageCoverageInfo, Module module) {
      myDirectoryCoverage.put(virtualFile, packageCoverageInfo);
    }

    @Override
    public void annotateTestDirectory(VirtualFile virtualFile, PackageAnnotator.PackageCoverageInfo packageCoverageInfo, Module module) {
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

