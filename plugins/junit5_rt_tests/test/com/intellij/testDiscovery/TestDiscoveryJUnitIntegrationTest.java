// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testDiscovery;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testDiscovery.TestDiscoveryDataSocketListener;
import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.execution.testDiscovery.TestDiscoveryIndex;
import com.intellij.execution.testDiscovery.actions.ShowAffectedTestsAction;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

// TODO parametrize by TD protocol
// TODO get agent from sources
@RunWith(Parameterized.class)
public class TestDiscoveryJUnitIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Parameterized.Parameter
  public String myJUnitVersion;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(createParams("4.12"),
                         createParams("4.11"),
                         createParams("4.10"));
  }

  private static Object[] createParams(final String version) {
    return new Object[]{version};
  }

  @Override
  protected void setupModule() throws Exception {
    ModuleRootModificationUtil.updateModel(myModule,
                                           model -> {
                                             ContentEntry entry = model.addContentEntry(getTestContentRoot());
                                             entry.addSourceFolder(getTestContentRoot() + "/src", false);
                                             entry.addSourceFolder(getTestContentRoot() + "/test", true);
                                           });
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", myJUnitVersion), getRepoManager());
  }

  @Before
  public void before() {
    Registry.get(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY).setValue(true, myProject);
  }

  @Test
  public void testSimpleIndex() throws Exception {
    runTestConfiguration(findClass(myModule, "PersonTest"));
    assertTestDiscoveryIndex("Person", "getName", t("PersonTest", "testPersonName"));
  }

  @Test
  public void testFewTestsInClass() throws Exception {
    runTestConfiguration(findClass(myModule, "PersonTest"));
    assertTestDiscoveryIndex("Person", "getName", t("PersonTest", "testPersonName1"), t("PersonTest", "testPersonName2"));
  }

  @Test
  public void testFewTestClasses() throws Exception {
    runTestConfiguration(myJavaFacade.findPackage(""));
    assertTestDiscoveryIndex("Person", "getName", t("PersonTest1", "testPersonName"), t("PersonTest2", "testPersonName"));
  }

  @Test
  public void testClassLoaderMagic() throws Exception {
    runTestConfiguration(findClass(myModule, "Test"));
    assertTestDiscoveryIndex("Magic", "abracadabra", t("Test", "testClassLoaderMagic"));
  }

  @Test
  public void testConstructor() throws Exception {
    runTestConfiguration(findClass(myModule, "PersonTest"));
    assertTestDiscoveryIndex("Person", "<init>", t("PersonTest", "testPerson"));
  }

  private void assertTestDiscoveryIndex(String className, String methodName, Pair<String, String>... expectedTests) {
    PsiClass aClass = myJavaFacade.findClass(className);
    PsiMethod method = "<init>".equals(methodName)
                       ? assertOneElement(aClass.getConstructors())
                       : assertOneElement(aClass.findMethodsByName(methodName, false));
    Couple<String> methodKey = ShowAffectedTestsAction.getMethodKey(method);

    TestDiscoveryIndex testDiscoveryIndex = TestDiscoveryIndex.getInstance(myProject);
    MultiMap<String, String> rawActualTests1 =
      testDiscoveryIndex.getTestsByMethodName(methodKey.getFirst(), methodKey.getSecond(), JUnitConfiguration.FRAMEWORK_ID);
    MultiMap<String, String> rawActualTests2 = testDiscoveryIndex.getTestsByClassName(className, JUnitConfiguration.FRAMEWORK_ID);

    Set<Pair<String, String>> actualTests1 =
      rawActualTests1.entrySet().stream().flatMap(e -> e.getValue().stream().map(m -> Pair.create(e.getKey(), m)))
                     .collect(Collectors.toSet());
    Set<Pair<String, String>> actualTests2 =
      rawActualTests2.entrySet().stream().flatMap(e -> e.getValue().stream().map(m -> Pair.create(e.getKey(), m)))
                     .collect(Collectors.toSet());
    assertEquals(ContainerUtil.newHashSet(expectedTests), actualTests1);
    assertEquals(ContainerUtil.newHashSet(expectedTests), actualTests2);

    Set<String> modules = actualTests1
      .stream()
      .flatMap(
        test -> testDiscoveryIndex.getTestModulesByMethodName(test.getFirst(), test.getSecond(), JUnitConfiguration.FRAMEWORK_ID).stream())
      .collect(Collectors.toSet());
    String module = assertOneElement(modules);
    assertEquals(myModule.getName(), module);

    for (Pair<String, String> test : expectedTests) {
      assertTrue(testDiscoveryIndex.hasTestTrace(test.getFirst(), test.getSecond(), JUnitConfiguration.FRAMEWORK_ID));
    }
    assertFalse(testDiscoveryIndex.hasTestTrace("dummy test name", "123", JUnitConfiguration.FRAMEWORK_ID));

  }

  private static Pair<String, String> t(String testClassName, String testMethodName) {
    return Pair.create(testClassName, testMethodName);
  }

  private void runTestConfiguration(@NotNull PsiElement psiElement) throws ExecutionException {
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE, myModule);
    JUnitConfiguration configuration = createConfiguration(psiElement, context);
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    TestDiscoveryDataSocketListener socketListener =
      configuration.getUserData(TestDiscoveryExtension.SOCKET_LISTENER_KEY);
    socketListener.awaitTermination();
    configuration.putUserData(TestDiscoveryExtension.SOCKET_LISTENER_KEY, null);
    assertEmpty(processOutput.err);
  }

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/testDiscovery/" +
                                 getTestName(true));
  }
}
