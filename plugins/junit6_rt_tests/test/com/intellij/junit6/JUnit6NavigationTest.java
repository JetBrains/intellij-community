// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.junit6.ServiceMessageUtil.normalizedTestOutput;

@DisplayName("junit 6 navigation features: location strings, etc")
public class JUnit6NavigationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit6_rt_tests/testData/integration/navigation");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test", true));
    final ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", JUnit6Constants.VERSION),
                 repoManager);
  }

  public void testNavigation() throws Exception {
    ProcessOutput output = doStartTestsProcess(createRunClassConfiguration("org.example.impl.NavTest"));
    assertEmpty(output.err);

    String messages = output.messages.stream().filter(m -> m.getAttributes().containsKey("locationHint"))
      .map(m -> normalizedTestOutput(m, Map.of("locationHint", (value) -> value.startsWith("file://") ? "file://##path##" : value)))
      .collect(Collectors.joining("\n"));

    assertEquals("""
                   ##TC[suiteTreeStarted id='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[test-factory:fileSourceDynamicTests()|]' name='fileSourceDynamicTests()' nodeId='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[test-factory:fileSourceDynamicTests()|]' parentNodeId='0' locationHint='java:test://org.example.impl.NavTest/fileSourceDynamicTests' metainfo='']
                   ##TC[suiteTreeNode id='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[method:methodNavigation()|]' name='methodNavigation()' nodeId='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[method:methodNavigation()|]' parentNodeId='0' locationHint='java:test://org.example.impl.NavTest/methodNavigation' metainfo='']
                   ##TC[testSuiteStarted id='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[test-factory:fileSourceDynamicTests()|]' name='fileSourceDynamicTests()' nodeId='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[test-factory:fileSourceDynamicTests()|]' parentNodeId='0' locationHint='java:test://org.example.impl.NavTest/fileSourceDynamicTests' metainfo='']
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[test-factory:fileSourceDynamicTests()|]/|[dynamic-test:#1|]' name='fileSource' nodeId='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[test-factory:fileSourceDynamicTests()|]/|[dynamic-test:#1|]' parentNodeId='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[test-factory:fileSourceDynamicTests()|]' locationHint='file://##path##']
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[method:methodNavigation()|]' name='methodNavigation()' nodeId='|[engine:junit-jupiter|]/|[class:org.example.impl.NavTest|]/|[method:methodNavigation()|]' parentNodeId='0' locationHint='java:test://org.example.impl.NavTest/methodNavigation' metainfo='']""",
                 messages);
  }

  @NotNull
  private RunConfiguration createRunClassConfiguration(final String className) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);

    RunConfiguration configuration = createConfiguration(aClass);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }
}
