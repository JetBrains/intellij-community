// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

public class SpockIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/spock");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.codehaus.groovy:groovy:2.5.4"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.spockframework:spock-core:1.2-groovy-2.5"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.4.0"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-engine", "1.4.0"), repoManager);
  }

  public void testRunClass() throws ExecutionException {
    PsiClass psiClass = findClass(myModule, "TestSpec");
    assertNotNull(psiClass);
    PsiMethod testMethod = psiClass.findMethodsByName("simple", false)[0];
    JUnitConfiguration configuration = createConfiguration(testMethod);
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    assertSize(5, processOutput.err); //WARNING: An illegal reflective access operation has occurred
    String testOutput = processOutput.out.toString();
    assertTrue(testOutput.contains("Test1"));
  }
}
