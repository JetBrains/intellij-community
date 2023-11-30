// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class SpockIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/spock");
  }
  
  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {"1.2-groovy-2.5"}, new Object[] {"2.0-groovy-2.5"});
  }
  
  @Parameterized.Parameter
  public String mySpockVersion;


  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.codehaus.groovy:groovy:2.5.23"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.spockframework:spock-core:" + mySpockVersion), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.4.0"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-engine", "1.4.0"), repoManager);
  }

  @Test
  public void testRunClass() throws ExecutionException {
    PsiClass psiClass = findClass(myModule, "TestSpec");
    assertNotNull(psiClass);
    PsiMethod testMethod = psiClass.findMethodsByName("simple", false)[0];
    JUnitConfiguration configuration = createConfiguration(testMethod);
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    String testOutput = processOutput.out.toString();
    assertTrue(testOutput.contains("Test1"));
  }
}
