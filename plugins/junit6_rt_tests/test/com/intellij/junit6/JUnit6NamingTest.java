// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;

public class JUnit6NamingTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit6_rt_tests/testData/integration/naming");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test", true));
    final ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", JUnit6Constants.VERSION), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-suite-api", JUnit6Constants.VERSION), repoManager);
  }

  public void testArrayParameters() {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("MyTest", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);
    PsiJavaFile file = (PsiJavaFile)aClass.getContainingFile();
    String[] methodPresentations =
      Arrays.stream(file.getClasses()[0].getMethods())
        .map(method -> JUnitConfiguration.Data.getMethodPresentation(method))
        .toArray(String[]::new);
    Assertions.assertArrayEquals(new String[]{"foo(int[])",
                                   "foo(int)",
                                   "foo([Ljava.lang.String;)",
                                   "foo(java.lang.String)",
                                   "foo([LMyTest$Foo;)",
                                   "foo(MyTest$Foo)"},
                                 methodPresentations,
                                 Arrays.toString(methodPresentations));
  }
}
