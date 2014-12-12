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
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.util.containers.hash.HashMap;

import java.io.File;
import java.util.Map;

/**
 * @author yole
 */
public class PackageAnnotatorTest extends ModuleTestCase {
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
    File coverageFile = new File(getTestDataPath(), "simple$foo_in_simple.coverage");
    IDEACoverageRunner runner = CoverageRunner.getInstance(IDEACoverageRunner.class);
    CoverageFileProvider fileProvider = new DefaultCoverageFileProvider(coverageFile);
    CoverageSuite suite =
      JavaCoverageEngine.getInstance().createCoverageSuite(runner, "Simple", fileProvider, null, -1, null, false, false, false);
    CoverageSuitesBundle bundle = new CoverageSuitesBundle(suite);
    PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage("foo");
    PackageAnnotator annotator = new PackageAnnotator(psiPackage);
    PackageAnnotationConsumer consumer = new PackageAnnotationConsumer();
    annotator.annotate(bundle, consumer);
    PackageAnnotator.ClassCoverageInfo fooClassCoverage = consumer.myClassCoverageInfo.get("foo.FooClass");
    assertNotNull(fooClassCoverage);
  }

  private static class PackageAnnotationConsumer implements PackageAnnotator.Annotator {
    private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirectoryCoverage =
      new HashMap<VirtualFile, PackageAnnotator.PackageCoverageInfo>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverage =
      new HashMap<String, PackageAnnotator.PackageCoverageInfo>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlatPackageCoverage =
      new HashMap<String, PackageAnnotator.PackageCoverageInfo>();
    private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfo =
      new HashMap<String, PackageAnnotator.ClassCoverageInfo>();

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

