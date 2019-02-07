// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.idea.HardwareAgentRequired;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author cdr
 */
@HardwareAgentRequired
public class PropertiesPerformanceTest extends CodeInsightTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ReferenceProvidersRegistry.getInstance();
  }

  @NotNull
  @Override
  protected Module createMainModule() throws IOException {
    String root = PluginPathManager.getPluginHomePath("java-i18n") + "/testData/performance/" + getTestName(true);
    VirtualFile tempProjectRootDir = PsiTestUtil.createTestProjectStructure(myProject, null, root, myFilesToDelete, false);
    Module module = loadAllModulesUnder(tempProjectRootDir);
    return module != null ? module : super.createMainModule();
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/performance/";
  }

  public void testTypingInBigFile() throws Exception {
    configureByFile(getTestName(true) + "/File1.properties");
    PlatformTestUtil.startPerformanceTest(getTestName(false), 100, () -> {
      type(' ');
      PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
      backspace();
      PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
    }).assertTiming();
  }

  public void testResolveManyLiterals() throws Exception {
    final PsiClass aClass = generateTestFiles();
    assertNotNull(aClass);
    PlatformTestUtil.startPerformanceTest(getTestName(false), 2000, () -> aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        PsiReference[] references = expression.getReferences();
        for (PsiReference reference : references) {
          reference.resolve();
        }
      }
    })).useLegacyScaling().assertTiming();
  }

  private PsiClass generateTestFiles() throws IOException {
    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(myModule).getSourceRoots();
    assertTrue(sourceRoots.length > 0);
    final String src = sourceRoots[0].getPath();
    String className = "PropRef";

    try (FileWriter classWriter = new FileWriter(new File(src, className + ".java"))) {
      classWriter.write("class " + className + "{");
      for (int f = 0; f < 100; f++) {
        try (FileWriter writer = new FileWriter(new File(src, "prop" + f + ".properties"))) {
          for (int i = 0; i < 10; i++) {
            String key = "prop." + f + ".number." + i;
            writer.write(key + "=" + key + "\n");
            classWriter.write("String s_" + f + "_" + i + "=\"" + key + "\";\n");
          }
        }
      }
      classWriter.write("}");
    }

    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(src);
    assertNotNull(src, virtualFile);
    virtualFile.refresh(false, true);

    final PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(myProject));
    assert aClass != null;
    PsiDocumentManager.getInstance(getProject()).getDocument(aClass.getContainingFile());

    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        expression.getNode();
      }
    });
    for (int f = 0; f < 100; f++) {
      for (int i = 0; i < 10; i++) {
        String key = "prop." + f + ".number." + i;
        PropertiesImplUtil.findPropertiesByKey(getProject(), key);
      }
    }

    return aClass;
  }
}
