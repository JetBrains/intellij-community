/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.coverage;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.util.containers.hash.HashMap;

import java.io.File;
import java.util.Map;

/**
 * @author yole
 */
public class CoverageIntegrationTest extends ModuleTestCase {
  private static String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("coverage") + "/testData/simple";
  }

  @Override
  protected void setUpProject() throws Exception {
    String testDataPath = getTestDataPath();
    myProject = ProjectManagerEx.getInstanceEx().loadProject(testDataPath);
    ProjectManagerEx.getInstanceEx().openTestProject(myProject);
    runStartupActivities();
  }

  public void testSimple() {
    CoverageSuitesBundle bundle = loadCoverageSuite(IDEACoverageRunner.class, "simple$foo_in_simple.coverage");
    PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage("foo");
    PackageAnnotator annotator = new PackageAnnotator(psiPackage);
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    annotator.annotate(bundle, consumer);
    PackageAnnotator.ClassCoverageInfo barClassCoverage = consumer.myClassCoverageInfo.get("foo.bar.BarClass");
    assertEquals(3, barClassCoverage.totalMethodCount);
    assertEquals(1, barClassCoverage.coveredMethodCount);
    PackageAnnotator.PackageCoverageInfo barPackageCoverage = consumer.myPackageCoverage.get("foo.bar");
    assertEquals(2, barPackageCoverage.coveredLineCount);
    assertEquals(9, barPackageCoverage.totalLineCount);
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
    private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirectoryCoverage =
      new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverage =
      new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlatPackageCoverage =
      new HashMap<>();
    private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfo =
      new HashMap<>();

    public void annotateSourceDirectory(VirtualFile virtualFile, PackageAnnotator.PackageCoverageInfo packageCoverageInfo, Module module) {
      myDirectoryCoverage.put(virtualFile, packageCoverageInfo);
    }

    public void annotateTestDirectory(VirtualFile virtualFile, PackageAnnotator.PackageCoverageInfo packageCoverageInfo, Module module) {
      myDirectoryCoverage.put(virtualFile, packageCoverageInfo);
    }

    public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
      myPackageCoverage.put(packageQualifiedName, packageCoverageInfo);
    }

    public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo, boolean flatten) {
      (flatten ? myFlatPackageCoverage : myPackageCoverage).put(packageQualifiedName, packageCoverageInfo);
    }

    public void annotateClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
      myClassCoverageInfo.put(classQualifiedName, classCoverageInfo);
    }
  }
}

