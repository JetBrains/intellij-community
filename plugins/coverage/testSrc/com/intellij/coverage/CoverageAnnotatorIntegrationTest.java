// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.coverage.analysis.CoverageInfoCollector;
import com.intellij.coverage.analysis.JavaCoverageClassesAnnotator;
import com.intellij.coverage.analysis.PackageAnnotator;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.JavaModuleTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CoverageAnnotatorIntegrationTest extends JavaModuleTestCase {
  private CompilerTester myCompilerTester;

  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PluginPathManager.getPluginHomePath("coverage") + "/testData/annotator");
  }


  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_11;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCompilerTester = new CompilerTester(myModule);
    List<CompilerMessage> compilerMessages = myCompilerTester.rebuild();
    assertEmpty(compilerMessages.stream()
                                .filter(message -> message.getCategory() == CompilerMessageCategory.ERROR)
                                .collect(Collectors.toSet()));
  }

  @Override
  protected void setUpModule() {
    super.setUpModule();
    ModuleRootModificationUtil.updateModel(myModule,
                                           model -> {
                                             ContentEntry contentEntry = model.addContentEntry(getTestContentRoot());
                                             contentEntry.addSourceFolder(getTestContentRoot() + "/src", false);
                                             contentEntry.addSourceFolder(getTestContentRoot() + "/src1", false);
                                             contentEntry.addSourceFolder(getTestContentRoot() + "/test", true);
                                           });
  }

  @Test
  public void testExcludeEverythingFromCoverage() {
    JavaCoverageEngine engine = new JavaCoverageEngine() {
      @Override
      public boolean acceptedByFilters(@NotNull PsiFile psiFile, @NotNull CoverageSuitesBundle suite) {
        return false;
      }
    };
    CoverageSuitesBundle suite = new CoverageSuitesBundle(new JavaCoverageSuite(engine)) {
      @NotNull
      @Override
      public ProjectData getCoverageData() {
        return new ProjectData() {
          @Override
          public ClassData getClassData(String name) {
            throw new RuntimeException("No classes are accepted by filter");
          }
        };
      }
    };
    new JavaCoverageClassesAnnotator(suite, myProject, new CoverageInfoCollector() {
      @Override
      public void addClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
        Assert.fail("No classes are accepted by filter");
      }
    }).visitSuite();
  }

  @Test
  public void testMultipleSourceRoots() {
    CoverageSuitesBundle suite = new CoverageSuitesBundle(new JavaCoverageSuite(new JavaCoverageEngine())) {
      @NotNull
      @Override
      public ProjectData getCoverageData() {
        return new ProjectData() {
          @Override
          public ClassData getClassData(String name) {
            ClassData data = new ClassData(name);
            LineData lineData = new LineData(1, "foo");
            lineData.setHits(1);
            data.registerMethodSignature(lineData);
            LineData lineData1 = new LineData(2, "bar");
            data.registerMethodSignature(lineData1);
            data.setLines(new LineData[] {lineData, lineData1});
            return data;
          }
        };
      }
    };
    JavaCoverageSuite javaCoverageSuite = (JavaCoverageSuite)suite.getSuites()[0];
    javaCoverageSuite.setIncludeFilters(new String[]{"p.*"});
    Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> dirs = new HashMap<>();
    new JavaCoverageClassesAnnotator(suite, myProject, new CoverageInfoCollector() {
      @Override
      public void addSourceDirectory(VirtualFile virtualFile,
                                     PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
        dirs.put(virtualFile, packageCoverageInfo);
      }
    }).visitSuite();

    assertEquals(2, dirs.size());
    for (PackageAnnotator.PackageCoverageInfo coverageInfo : dirs.values()) {
      assertTrue(coverageInfo instanceof PackageAnnotator.DirCoverageInfo);
      assertEquals(1, coverageInfo.coveredClassCount);
      assertEquals(1, coverageInfo.coveredLineCount);
      assertEquals(1, coverageInfo.coveredMethodCount);
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myCompilerTester.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
