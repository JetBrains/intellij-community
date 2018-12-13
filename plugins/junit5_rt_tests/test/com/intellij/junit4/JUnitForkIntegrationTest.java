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
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.LinkedHashSet;

public class JUnitForkIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/forkProject");
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
    });

    JpsMavenRepositoryLibraryDescriptor junit4Lib =
      new JpsMavenRepositoryLibraryDescriptor("junit", "junit", "4.12");
    addLibs(myModule, junit4Lib, repoManager);
    addLibs(module2, junit4Lib, repoManager);

    JpsMavenRepositoryLibraryDescriptor junit5Lib =
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.3.0");
    addLibs(myModule, junit5Lib, repoManager);
    addLibs(module2, junit5Lib, repoManager);
  }

  public void testForkPerModule() throws ExecutionException {
    doTestForkPerModule(createRunPackageConfiguration("junit4"));
  }

  public void testForkPerModuleWithDependency() throws ExecutionException {
    doTestForkPerModule(createRunPackageConfiguration("junit4"));
  }

  public void testForkPatternPerModuleWithDependency() throws ExecutionException {
    JUnitConfiguration configuration = new JUnitConfiguration("pattern", getProject());
    JUnitConfiguration.Data data = configuration.getPersistentData();
    data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
    LinkedHashSet<String> pattern = new LinkedHashSet<>();
    pattern.add(".*MyTest.*");
    data.setPatterns(pattern);
    doTestForkPerModule(configuration);
  }

  private static void doTestForkPerModule(final JUnitConfiguration configuration) throws ExecutionException {
    configuration.setWorkingDirectory("$MODULE_WORKING_DIR$");
    configuration.setSearchScope(TestSearchScope.WHOLE_PROJECT);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertTrue(processOutput.sys.toString().contains("-junit5"));
    assertEmpty(processOutput.out);
    assertSize(2, ContainerUtil.filter(processOutput.messages, TestFailed.class::isInstance));
  }

  public void testForkPerClassOnTwoEngines() throws ExecutionException {
    final JUnitConfiguration configuration = createRunPackageConfiguration("klass");
    configuration.setSearchScope(TestSearchScope.SINGLE_MODULE);
    configuration.setModule(myModule);
    configuration.setForkMode(JUnitConfiguration.FORK_KLASS);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertTrue(processOutput.sys.toString().contains("-junit5"));
    assertEmpty(processOutput.out);
    assertSize(2, ContainerUtil.filter(processOutput.messages, TestStarted.class::isInstance));
  }

  public void testForkPerClassOnTwoEnginesWithRepeat() throws ExecutionException {
    final JUnitConfiguration configuration = createRunPackageConfiguration("klass");
    configuration.setSearchScope(TestSearchScope.SINGLE_MODULE);
    configuration.setModule(myModule);
    configuration.setForkMode(JUnitConfiguration.FORK_KLASS);

    configuration.setRepeatCount(2);
    configuration.setRepeatMode(RepeatCount.N);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertTrue(processOutput.sys.toString().contains("-junit5"));
    assertEmpty(processOutput.out);
    assertSize(4, ContainerUtil.filter(processOutput.messages, TestStarted.class::isInstance));
  }

  public void testForkPerClassOnRepeat() throws ExecutionException {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("klass");
    assertNotNull(aPackage);
    JUnitConfiguration configuration = createConfiguration(aPackage);
    configuration.setModule(myModule);
    configuration.setForkMode(JUnitConfiguration.FORK_KLASS);
    configuration.setRepeatCount(2);
    configuration.setRepeatMode(RepeatCount.N);
    JUnitConfiguration.Data data = configuration.getPersistentData();
    data.TEST_OBJECT = JUnitConfiguration.TEST_CLASS;
    data.MAIN_CLASS_NAME = "klass.MyTest5";

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertTrue(processOutput.sys.toString().contains("-junit5"));
    assertEmpty(processOutput.out);
    assertSize(2, ContainerUtil.filter(processOutput.messages, TestStarted.class::isInstance));
  }

  @NotNull
  private JUnitConfiguration createRunPackageConfiguration(final String packageName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
    assertNotNull(aPackage);
    RunConfiguration configuration = createConfiguration(aPackage);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return (JUnitConfiguration)configuration;
  }
}
