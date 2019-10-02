// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

//RUN ON JAVA 11 ONLY
public class JUnitForkWithModuleInfoIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/forkProjectWithModuleInfo");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    
    final ArtifactRepositoryManager repoManager = getRepoManager();
    Module module2 = createEmptyModule();
    
    ModuleRootModificationUtil.setModuleSdk(module2, JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
    ModuleRootModificationUtil.updateModel(module2, model -> {
      String contentUrl = getTestContentRoot() + "/module2";
      ContentEntry contentEntry = model.addContentEntry(contentUrl);
      contentEntry.addSourceFolder(contentUrl + "/test", true);
      //add dependency between modules
      if (getTestName(false).endsWith("WithDependency")) {
        model.addModuleOrderEntry(myModule);
      }
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_11);
    });

    ModuleRootModificationUtil.updateModel(myModule, model -> {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_11);
    });

    JpsMavenRepositoryLibraryDescriptor junit5Lib =
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.5.2");
    addMavenLibs(myModule, junit5Lib, repoManager);
    addMavenLibs(module2, junit5Lib, repoManager);
  }

  public void testForkPerModuleForModuleInfoInTestRoot() throws ExecutionException {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("p");
    assertNotNull(aPackage);
    RunConfiguration runConfiguration = createConfiguration(aPackage);
    assertInstanceOf(runConfiguration, JUnitConfiguration.class);
    final JUnitConfiguration configuration = (JUnitConfiguration)runConfiguration;
    configuration.setWorkingDirectory("$MODULE_WORKING_DIR$");
    configuration.setSearchScope(TestSearchScope.WHOLE_PROJECT);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertTrue(processOutput.sys.toString().contains("-junit5"));
    assertEmpty(processOutput.out);
    assertSize(3, ContainerUtil.filter(processOutput.messages, TestStarted.class::isInstance));
  }

  public void testForkPerMethodForModuleInfoInTestRoot() throws ExecutionException {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("p.MyTest1",
                                                                       GlobalSearchScope.projectScope(getProject()));
    assertNotNull(aClass);
    RunConfiguration runConfiguration = createConfiguration(aClass);
    assertInstanceOf(runConfiguration, JUnitConfiguration.class);
    final JUnitConfiguration configuration = (JUnitConfiguration)runConfiguration;
    configuration.setForkMode(JUnitConfiguration.FORK_METHOD);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertTrue(processOutput.sys.toString().contains("-junit5"));
    assertEmpty(processOutput.out);
    assertSize(2, ContainerUtil.filter(processOutput.messages, TestStarted.class::isInstance));
  }
}
