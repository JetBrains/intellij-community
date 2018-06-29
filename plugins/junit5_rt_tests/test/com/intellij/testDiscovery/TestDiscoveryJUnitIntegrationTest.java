// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testDiscovery;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testDiscovery.TestDiscoveryDataSocketListener;
import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.execution.testDiscovery.TestDiscoveryIndex;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

// TODO parametrize by TD protocol
// TODO get agent from sources
@RunsInEdt
@RunWith(Parameterized.class)
public class TestDiscoveryJUnitIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Rule public final EdtRule edtRule = new EdtRule();
  @Rule public final TestName myNameRule = new TestName();

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
    addLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", myJUnitVersion), getRepoManager());
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY).setValue(true, myProject);
  }

  @After
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
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

  private void assertTestDiscoveryIndex(String className, String methodName, Pair<String, String>... expectedTests) throws IOException {
    TestDiscoveryIndex testDiscoveryIndex = TestDiscoveryIndex.getInstance(myProject);
    MultiMap<String, String> rawActualTests1 =
      testDiscoveryIndex.getTestsByMethodName(className, methodName, JUnitConfiguration.FRAMEWORK_ID);
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
  }

  private static Pair<String, String> t(String testClassName, String testMethodName) {
    return Pair.create(testClassName, testMethodName);
  }

  private void runTestConfiguration(@NotNull PsiElement psiElement) throws ExecutionException {
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE, myModule);
    RunConfiguration configuration = createConfiguration(psiElement, context);
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    TestDiscoveryDataSocketListener socketListener =
      ((RunConfigurationBase)configuration).getUserData(TestDiscoveryExtension.SOCKET_LISTENER_KEY);
    socketListener.awaitTermination();
    ((RunConfigurationBase)configuration).putUserData(TestDiscoveryExtension.SOCKET_LISTENER_KEY, null);
    assertEmpty(processOutput.err);
  }

  @Override
  public String getName() {
    return myNameRule.getMethodName();
  }
  
  protected String getTestContentRoot() {
    String methodName = myNameRule.getMethodName();
    methodName = methodName.substring(0, methodName.indexOf("["));
    methodName = StringUtil.decapitalize(StringUtil.trimStart(methodName, "test"));
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/testDiscovery/" + methodName);
  }
}
