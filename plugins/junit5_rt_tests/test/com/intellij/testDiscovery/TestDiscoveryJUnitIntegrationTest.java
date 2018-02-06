// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testDiscovery;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.execution.testDiscovery.TestDiscoveryIndex;
import com.intellij.junit4.JUnitAbstractIntegrationTest;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// TODO parametrize by 1) junit version 2) TD protocol
public class TestDiscoveryJUnitIntegrationTest extends JUnitAbstractIntegrationTest {
  private static final String myJUnitVersion = "4.12";
  private CompilerTester myCompilerTester;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY).setValue(true, myProject);

    ModuleRootModificationUtil.updateModel(myModule,
                                           model -> {
                                             ContentEntry entry = model.addContentEntry(getTestContentRoot());
                                             entry.addSourceFolder(getTestContentRoot() + "/src", false);
                                             entry.addSourceFolder(getTestContentRoot() + "/test", true);
                                           });
    addLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", myJUnitVersion), getRepoManager());
    myCompilerTester = new CompilerTester(myModule);
    List<CompilerMessage> compilerMessages = myCompilerTester.rebuild();
    assertEmpty(compilerMessages.stream()
                                .filter(message -> message.getCategory() == CompilerMessageCategory.ERROR)
                                .collect(Collectors.toSet()));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myCompilerTester.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSimpleIndex() throws Exception {
    runTestConfiguration(findClass(myModule, "PersonTest"));
    assertTestDiscoveryIndex("Person", "getName", t("PersonTest", "testPersonName"));
  }

  public void testFewTestsInClass() throws Exception {
    runTestConfiguration(findClass(myModule, "PersonTest"));
    assertTestDiscoveryIndex("Person", "getName", t("PersonTest", "testPersonName1"), t("PersonTest", "testPersonName2"));
  }

  public void testFewTestClasses() throws Exception {
    runTestConfiguration(myJavaFacade.findPackage(""));
    assertTestDiscoveryIndex("Person", "getName", t("PersonTest1", "testPersonName"), t("PersonTest2", "testPersonName"));
  }

  public void _testConstructor() throws Exception {
    runTestConfiguration(findClass(myModule, "PersonTest"));
    assertTestDiscoveryIndex("Person", "<init>", t("PersonTest", "testPerson"));
  }

  private void assertTestDiscoveryIndex(String className, String methodName, Pair<String, String>... expectedTests) throws IOException {
    Collection<String> rawActualTests = TestDiscoveryIndex.getInstance(myProject).getTestsByMethodName(className, methodName, "j");
    Set<Pair<String, String>> actualTests = rawActualTests.stream().map(test -> {
      int separatorIndex = test.lastIndexOf('-');
      return Pair.create(test.substring(0, separatorIndex), test.substring(separatorIndex + 1));
    }).collect(Collectors.toSet());
    assertEquals(ContainerUtil.newHashSet(expectedTests), actualTests);
  }

  private static Pair<String, String> t(String testClassName, String testMethodName) {
    return Pair.create(testClassName, testMethodName);
  }

  private void runTestConfiguration(@NotNull PsiElement psiElement) throws ExecutionException {
    ProcessOutput processOutput = doStartTestsProcess(createConfiguration(psiElement));
    assertEmpty(processOutput.err);
  }

  private String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/testDiscovery/" + getTestName(true));
  }
}
