// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

public class JUnitModuleInfoIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/splitModulePath");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    
    ModuleRootModificationUtil.updateModel(myModule, model -> {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_11);
    });

    final ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.hamcrest", "hamcrest-library", "1.3"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.hamcrest", "hamcrest-core", "1.3"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.5.2"), repoManager);
  }

  
  public void testModulePathSplit() throws ExecutionException {
    doTest();
  }

  public void testModulePathSplitExplicitLauncher() throws Exception {
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-launcher", "1.5.2"), getRepoManager());
    doTest();
  }

  private void doTest() throws ExecutionException {
    @Nullable PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("a.Test1", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);
    RunConfiguration runConfiguration = createConfiguration(aClass);
    assertInstanceOf(runConfiguration, JUnitConfiguration.class);
    final JUnitConfiguration configuration = (JUnitConfiguration)runConfiguration;

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertTrue(processOutput.sys.toString().contains("-junit5"));
    assertEmpty(processOutput.out);
    assertSize(1, ContainerUtil.filter(processOutput.messages, TestStarted.class::isInstance));
  }

}
