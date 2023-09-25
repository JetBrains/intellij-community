// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.coverage.analysis.Annotator;
import com.intellij.coverage.analysis.JavaCoverageClassesAnnotator;
import com.intellij.coverage.analysis.JavaCoverageReportEnumerator;
import com.intellij.coverage.analysis.PackageAnnotator;
import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import junit.framework.AssertionFailedError;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ExcludeFromTestDiscovery
public class CoverageIntegrationTest extends CoverageIntegrationBaseTest {

  public void testIJSuite() {
    CoverageSuitesBundle bundle = loadIJSuite();
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    new JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite();
    assertHits(consumer, false);
  }

  public void testSingleClassFilter() {
    String[] filters = new String[]{"foo.bar.BarClass"};
    CoverageSuitesBundle bundle = loadIJSuite(filters);
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
    CoverageSuitesBundle bundle = loadJaCoCoSuite();
    ClassData classData = bundle.getCoverageData().getClassData("foo.FooClass");
    // getStatus() never returns full coverage; it can only distinguish between none and partial
    assertEquals(LineCoverage.PARTIAL, classData.getStatus("method1()I").intValue());
  }

  public void testJaCoCo() {
    CoverageSuitesBundle bundle = loadJaCoCoSuite();
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    new JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite();
    assertHits(consumer, true);
  }

  public void testJaCoCoWithoutUnloaded() {
    CoverageSuitesBundle bundle = loadJaCoCoSuite();
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    JavaCoverageReportEnumerator.collectSummaryInReport(bundle, myProject, consumer);
    assertHits(consumer, true);
  }

  public void testMergeIjWithJaCoCo() {
    var ijSuite = loadIJSuite().getSuites()[0];
    var jacocoSuite = loadJaCoCoSuite().getSuites()[0];

    var bundle = new CoverageSuitesBundle(new CoverageSuite[]{ijSuite, jacocoSuite});
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    new JavaCoverageClassesAnnotator(bundle, myProject, consumer).visitSuite();
    assertHits(consumer, true);
  }

  private static void assertHits(PackageAnnotationConsumer consumer, boolean branches) {
    assertEquals(3, consumer.myClassCoverageInfo.size());
    assertEquals(2, consumer.myFlatPackageCoverage.size());
    assertEquals(3, consumer.myPackageCoverage.size());
    assertEquals(3, consumer.myDirectoryCoverage.size());

    int totalBranches = branches ? 2 : 0;
    int coveredBranches = branches ? 1 : 0;

    assertHits(consumer.myClassCoverageInfo.get("foo.bar.BarClass"), new int[]{1, 1, 3, 1, 4, 2, 0, 0});
    assertHits(consumer.myClassCoverageInfo.get("foo.bar.UncoveredClass"), new int[]{1, 0, 4, 0, 4, 0, 0, 0});
    assertHits(consumer.myClassCoverageInfo.get("foo.FooClass"), new int[]{1, 1, 2, 2, 3, 3, totalBranches, coveredBranches});

    assertHits(consumer.myFlatPackageCoverage.get("foo.bar"), new int[]{2, 1, 7, 1, 8, 2, 0, 0});
    assertHits(consumer.myFlatPackageCoverage.get("foo"), new int[]{1, 1, 2, 2, 3, 3, totalBranches, coveredBranches});

    assertHits(consumer.myPackageCoverage.get("foo.bar"), new int[]{2, 1, 7, 1, 8, 2, 0, 0});
    assertHits(consumer.myPackageCoverage.get("foo"), new int[]{3, 2, 9, 3, 11, 5, totalBranches, coveredBranches});
    assertHits(consumer.myPackageCoverage.get(""), new int[]{3, 2, 9, 3, 11, 5, totalBranches, coveredBranches});
  }

  public void testHTMLReport() throws IOException {
    CoverageSuitesBundle bundle = loadIJSuite();
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

  private static class PackageAnnotationConsumer implements Annotator {
    private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirectoryCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlatPackageCoverage = new HashMap<>();
    private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfo = new ConcurrentHashMap<>();

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

  private static void assertHits(PackageAnnotator.SummaryCoverageInfo info, int[] hits) {
    assertNotNull(info);
    assertEquals(hits[0], info.totalClassCount);
    assertEquals(hits[1], info.coveredClassCount);
    assertEquals(hits[2], info.totalMethodCount);
    assertEquals(hits[3], info.coveredMethodCount);
    assertEquals(hits[4], info.totalLineCount);
    assertEquals(hits[5], info.getCoveredLineCount());
    assertEquals(hits[6], info.totalBranchCount);
    assertEquals(hits[7], info.coveredBranchCount);
  }
}
