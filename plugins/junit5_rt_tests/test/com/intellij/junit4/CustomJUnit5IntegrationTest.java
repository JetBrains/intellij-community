// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

public class CustomJUnit5IntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    final ArtifactRepositoryManager repoManager = getRepoManager();
    addLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.2.0"), repoManager);
    addLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-engine", "1.2.0"), repoManager);
  }

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/custom5Project");
  }

  public void testRunClass() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("xxx.SomeTest", GlobalSearchScope.projectScope(myProject));
    RunConfiguration configuration = createConfiguration(aClass);

    ProcessOutput processOutput = doStartTestsProcess(configuration);
    assertEmpty(processOutput.out);
    assertContainsElements(processOutput.err, "java.lang.RuntimeException: The Bad. The Ugly. No Good\n");
  }
}
