// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit4;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.PlatformTestUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class SpockIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/spock");
  }

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[]{"2.0-groovy-3.0", "org.codehaus.groovy:groovy:3.0.25"},
                         new Object[]{"2.3-groovy-3.0", "org.codehaus.groovy:groovy:3.0.25"},
                         new Object[]{"2.3-groovy-4.0", "org.apache.groovy:groovy:4.0.28"}
    );
  }

  @Parameterized.Parameter
  public String spockVersion;

  @Parameterized.Parameter(1)
  public String groovyCoordinate;

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor(groovyCoordinate), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.spockframework:spock-core:" + spockVersion), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.4.0"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-engine", "1.4.0"), repoManager);
  }

  @Test
  public void testRunClass() throws ExecutionException {
    PsiClass psiClass = findClass(myModule, "TestSpec");
    assertNotNull(psiClass);
    JUnitConfiguration configuration = createConfiguration(psiClass);
    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertTrue(processOutput.out.toString().contains("Test1"));

    String actual = getNormalizedOutput(processOutput);
    assertEquals("""
                   ##TC[enteredTheMatrix]
                   ##TC[suiteTreeNode id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='simple' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/simple' metainfo='']
                   ##TC[suiteTreeNode id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='passing single test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/passing single test' metainfo='']
                   ##TC[suiteTreeNode id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='failing single test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/failing single test' metainfo='']
                   ##TC[suiteTreeNode id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='passing multiple-where test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/passing multiple-where test' metainfo='java.lang.Object']
                   ##TC[suiteTreeNode id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='failing multiple-where test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/failing multiple-where test' metainfo='java.lang.Object']
                   ##TC[treeEnded]
                   ##TC[rootName name='TestSpec' location='java:suite://TestSpec']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='simple' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/simple' metainfo='']
                   ##TC[testFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='simple' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='passing single test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/passing single test' metainfo='']
                   ##TC[testFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='passing single test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='failing single test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/failing single test' metainfo='']
                   ##TC[testFailed id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='failing single test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' message='Condition not satisfied:|n|nfalse|n' details='TRACE']
                   ##TC[testFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='failing single test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='passing multiple-where test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/passing multiple-where test' metainfo='java.lang.Object']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' name='passing multiple-where test |[something: 0, #0|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' locationHint='java:test://TestSpec/passing multiple-where test' metainfo='java.lang.Object']
                   ##TC[testFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' name='passing multiple-where test |[something: 0, #0|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' name='passing multiple-where test |[something: 1, #1|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' locationHint='java:test://TestSpec/passing multiple-where test' metainfo='java.lang.Object']
                   ##TC[testFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' name='passing multiple-where test |[something: 1, #1|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]']
                   ##TC[testSuiteFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='passing multiple-where test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='failing multiple-where test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0' locationHint='java:test://TestSpec/failing multiple-where test' metainfo='java.lang.Object']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' name='failing multiple-where test |[something: 0, #0|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' locationHint='java:test://TestSpec/failing multiple-where test' metainfo='java.lang.Object']
                   ##TC[testFailed id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' name='failing multiple-where test |[something: 0, #0|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' message='Condition not satisfied:|n|nsomething == (something + 1)|n||         ||   ||         |||n0         ||   0         1|n          false|n' expected='1|n' actual='0|n' details='TRACE']
                   ##TC[testFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' name='failing multiple-where test |[something: 0, #0|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:0|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]']
                   ##TC[testStarted id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' name='failing multiple-where test |[something: 1, #1|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' locationHint='java:test://TestSpec/failing multiple-where test' metainfo='java.lang.Object']
                   ##TC[testFailed id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' name='failing multiple-where test |[something: 1, #1|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' message='Condition not satisfied:|n|nsomething == (something + 1)|n||         ||   ||         |||n1         ||   1         2|n          false|n' expected='2|n' actual='1|n' details='TRACE']
                   ##TC[testFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' name='failing multiple-where test |[something: 1, #1|]' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]/|[iteration:1|]' parentNodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]']
                   ##TC[testSuiteFinished id='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' name='failing multiple-where test' nodeId='|[engine:spock|]/|[spec:TestSpec|]/|[feature:$spock_feature|]' parentNodeId='0']
                   """, actual);
  }

  private static String getNormalizedOutput(ProcessOutput processOutput) {
    String out = processOutput.messages.stream()
      .map(ServiceMessage::asString)
      .collect(Collectors.joining("\n"));
    return out
             .replace("##teamcity[", "##TC[")
             .replaceAll(" duration='[0-9]+'", "")
             .replaceAll(" durationStrategy='[^']*'", "")
             .replaceAll("details='.*?']", "details='TRACE']")
             .replaceAll("\\$spock_feature(_[0-9]+)+", "\\$spock_feature") + "\n";
  }
}
