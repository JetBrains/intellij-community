// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class JUnitVersionSwitchingIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(
      PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/versionSwitching"
    );
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test", true));
    ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", "3.8.2", false, List.of()), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.13.0", false, List.of()),
                 repoManager);
  }

  public void testFallbackToJUnit3WhenJUnit4NotOnClasspath() throws Exception {
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithLibrariesScope(myModule);

    assertNull("org.junit.Test must not be on the classpath to trigger JUNIT4→JUNIT3 fallback",
               JavaPsiFacade.getInstance(myProject).findClass("org.junit.Test", moduleScope));

    PsiClass testClass = JavaPsiFacade.getInstance(myProject).findClass("org.example.SimpleJUnit3Test",
                                                                        GlobalSearchScope.projectScope(myProject));
    assertNotNull("SimpleJUnit3Test not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    assertNotNull(configuration);

    ProcessOutput output = doStartTestsProcess(configuration,
                                               params -> replaceJUnitVersion(params, JUnitStarter.JUNIT4_PARAMETER));
    assertEmpty(output.err);

    assertTrue(output.sys.toString().contains("-junit4"));
    assertTrue(ContainerUtil.exists(output.out, s -> s.contains("junit4.classes.present=false")));

    List<ServiceMessage> messages = output.messages;
    assertEquals(2, messages.stream().filter(TestFinished.class::isInstance).count());
    assertEquals(0, messages.stream().filter(TestFailed.class::isInstance).count());
  }

  public void testJUnit5PlatformMissingShowsError() throws Exception {
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithLibrariesScope(myModule);

    assertNull("TestEngine must not be on the classpath to trigger JUnit Platform missing error",
               JavaPsiFacade.getInstance(myProject).findClass("org.junit.platform.engine.TestEngine", moduleScope));

    PsiClass testClass = JavaPsiFacade.getInstance(myProject)
      .findClass("org.example.SimpleJUnit5Test", GlobalSearchScope.projectScope(myProject));
    assertNotNull("SimpleJUnit5Test not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    assertNotNull(configuration);

    ProcessOutput output = doStartTestsProcess(configuration,
                                               params -> replaceJUnitVersion(params, JUnitStarter.JUNIT5_PARAMETER));

    assertTrue(output.sys.toString().contains("-junit5"));
    assertTrue("stderr must contain the 'JUnit Platform is not available' error message",
               ContainerUtil.exists(output.err, s -> s.contains("JUnit Platform is not available")));
    assertTrue("No test messages should be emitted when JUnit Platform is missing",
               output.messages.isEmpty());
  }

  public void testJUnit6FallsBackToJUnit5WhenJUnit6NotAvailable() throws Exception {
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.13.0"), getRepoManager());

    PsiClass testClass = JavaPsiFacade.getInstance(myProject)
      .findClass("org.example.SimpleJUnit5Test", GlobalSearchScope.projectScope(myProject));
    assertNotNull("SimpleJUnit5Test not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    assertNotNull(configuration);

    ProcessOutput output = doStartTestsProcess(configuration,
                                               params -> replaceJUnitVersion(params, JUnitStarter.JUNIT6_PARAMETER));

    assertTrue("stderr must contain the 'JUnit 6 cannot be used with the current test runtime classpath. Falling back to JUnit 5.' error message",
               ContainerUtil.exists(output.err, s -> s.contains("JUnit 6 cannot be used with the current test runtime classpath. Falling back to JUnit 5.")));

    assertTrue(output.sys.toString().contains("-junit6"));
    assertTrue("Test output must report that JUnit6 classes are absent (junit6.classes.present=false)",
               ContainerUtil.exists(output.out, s -> s.contains("junit6.classes.present=false")));

    List<ServiceMessage> messages = output.messages;
    assertEquals("Test should finish under JUNIT5 fallback runner", 1,
                 messages.stream().filter(TestFinished.class::isInstance).count());
    assertEquals("Test should not fail", 0,
                 messages.stream().filter(TestFailed.class::isInstance).count());
  }

  public void testJUnit6CheckFallsBackToJUnit5WhenJUnit5HasPriority() throws Exception {
    ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.13.0", false, List.of()),
                 repoManager);
    addMavenLibs(myModule,
                 new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-launcher", "1.13.0", false, List.of()),
                 repoManager);
    addMavenLibs(myModule,
                 new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-engine", "1.13.0", false, List.of()),
                 repoManager);
    addMavenLibs(myModule,
                 new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-commons", "1.13.0", false, List.of()),
                 repoManager);

    addMavenLibs(myModule,
                 new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-engine", "6.0.0", false, List.of()),
                 repoManager);

    IndexingTestUtil.waitUntilIndexesAreReady(myProject);

    PsiClass testClass = JavaPsiFacade.getInstance(myProject)
      .findClass("org.example.SimpleJUnit5Test", GlobalSearchScope.projectScope(myProject));
    assertNotNull("SimpleJUnit5Test not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    assertNotNull(configuration);

    ProcessOutput output = doStartTestsProcess(configuration, params -> replaceJUnitVersion(params, JUnitStarter.JUNIT6_PARAMETER));

    assertTrue("stderr must contain the 'JUnit 6 cannot be used with the current test runtime classpath. Falling back to JUnit 5.' error message",
               ContainerUtil.exists(output.err, s -> s.contains("JUnit 6 cannot be used with the current test runtime classpath. Falling back to JUnit 5.")));

    assertTrue(output.sys.toString().contains("-junit6"));
    assertTrue("Test output must report that JUnit6 classes are absent (junit6.classes.present=false)",
               ContainerUtil.exists(output.out, s -> s.contains("junit6.classes.present=false")));

    List<ServiceMessage> messages = output.messages;
    assertEquals("Test should finish under JUNIT5 fallback runner", 1,
                 messages.stream().filter(TestFinished.class::isInstance).count());
    assertEquals("Test should not fail", 0,
                 messages.stream().filter(TestFailed.class::isInstance).count());
  }

  public void testRunnerIgnoresJUnit6FromRuntimeScopeOnlyLib() throws Exception {
    Collection<File> junit6Files = getRepoManager().resolveDependency("org.junit.jupiter", "junit-jupiter-api", "6.0.0", false, List.of());

    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    JarFileSystem jarFileSystem = JarFileSystem.getInstance();
    List<@NotNull String> classesUrls = junit6Files.stream().map(localFileSystem::findFileByIoFile)
      .map(jarFileSystem::getJarRootForLocalFile)
      .map(VirtualFile::getUrl).toList();

    ModuleRootModificationUtil.addModuleLibrary(myModule, "junit6-runtime-only", classesUrls, List.of(), DependencyScope.RUNTIME);
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);

    // Package-level run must select -junit5, not -junit6
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("org.example");
    assertNotNull("Package org.example not found", aPackage);
    JUnitConfiguration configuration = createConfiguration(aPackage);
    configuration.getConfigurationModule().setModule(myModule);
    ProcessOutput output = doStartTestsProcess(configuration);
    assertTrue("Expected -junit5 in output, but got: " + output.sys, output.sys.toString().contains("-junit5"));
  }

  private static void replaceJUnitVersion(@NotNull JavaParameters parameters, @NotNull String version) {
    ParametersList list = parameters.getProgramParametersList();
    for (int i = 0; i < list.getParametersCount(); i++) {
      if (list.get(i).startsWith("-junit")) {
        list.set(i, version);
        break;
      }
    }
    if (version.equals(JUnitStarter.JUNIT6_PARAMETER)) {
      List<String> path = parameters.getClassPath().getPathList();
      path.stream().filter(s -> s.contains("junit-v5-rt")).findFirst()
        .map(s -> s.replace("junit-v5-rt", "junit-v6-rt").replace("junit5_rt", "junit6_rt"))
        .ifPresent(s -> {
          parameters.getClassPath().add(s);
        });
    }
  }
}
