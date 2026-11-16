// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.FileUrlProvider;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.junit.testFramework.MavenTestLib.JUNIT5;
import static com.intellij.pom.java.LanguageLevel.HIGHEST;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JUnitUniqueIdTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new JUnitProjectDescriptor(HIGHEST, JUNIT5));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testValidateUniqueId() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    PsiFile file = myFixture.addFileToProject("some.txt", "");
    ReadAction.runBlocking(() -> {
      SMTestProxy proxy = new SMTestProxy("test1", false, "file://" + file.getVirtualFile().getPath());
      proxy.setLocator(new FileUrlProvider());
      proxy.putUserData(SMTestProxy.NODE_ID, "nodeId");
      assertEquals("nodeId", TestUniqueId.getEffectiveNodeId(proxy, project, GlobalSearchScope.projectScope(project)));
    });
  }

  /** A repetition changes the id of the tree node. The runner then reports the id of the test framework apart from it. */
  @Test
  public void testOriginalIdReplacesTreeNodeId() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    PsiFile file = myFixture.addFileToProject("some.txt", "");
    ReadAction.runBlocking(() -> {
      SMTestProxy proxy = new SMTestProxy("test1", false, "file://" + file.getVirtualFile().getPath());
      proxy.setLocator(new FileUrlProvider());
      proxy.putUserData(SMTestProxy.NODE_ID, "[2]/nodeId");
      proxy.putUserData(SMTestProxy.ORIGINAL_NODE_ID, "nodeId");
      assertEquals("nodeId", TestUniqueId.getEffectiveNodeId(proxy, project, GlobalSearchScope.projectScope(project)));
    });
  }

  /** The suite of a repetition is no node of the test framework, so no unique id can run it. */
  @Test
  public void testSuiteOfRepetitionHasNoEffectiveNodeId() {
    Project project = codeInsightFixture.get().getProject();
    SMTestProxy proxy = new SMTestProxy("[2]", true, null);
    proxy.putUserData(SMTestProxy.NODE_ID, "[2]");
    proxy.putUserData(SMTestProxy.ORIGINAL_NODE_ID, "");
    ReadAction.runBlocking(() -> {
      assertNull(TestUniqueId.getEffectiveNodeId(proxy, project, GlobalSearchScope.projectScope(project)));
    });
  }

  @Test
  public void testGeneratedName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    PsiClass psiClass = myFixture.addClass("class MyTest {void m() {}}");

    ReadAction.runBlocking(() -> {
      SMTestProxy parent = new SMTestProxy("MyTest", true, "java:suite://MyTest");
      SMTestProxy proxy = new SMTestProxy("nodeId", false, "java:test://MyTest.m");
      proxy.setLocator(JavaTestLocator.INSTANCE);
      parent.addChild(proxy);
      proxy.putUserData(SMTestProxy.NODE_ID, "nodeId");
      assertEquals("nodeId", TestUniqueId.getEffectiveNodeId(proxy, project, GlobalSearchScope.projectScope(project)));
      RunConfigurationProducer<?> producer = RunConfigurationProducer.getInstance(UniqueIdConfigurationProducer.class);
      MapDataContext context = new MapDataContext();
      context.put(CommonDataKeys.PROJECT, project);
      context.put(AbstractTestProxy.DATA_KEYS, new AbstractTestProxy[]{proxy});
      context.put(AbstractTestProxy.DATA_KEY, proxy);
      context.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiClass.getMethods()[0]));
      JUnitConfiguration oldConfiguration = new JUnitConfiguration("", project);
      oldConfiguration.setModule(myFixture.getModule());
      context.put(RunConfiguration.DATA_KEY, oldConfiguration);

      ConfigurationFromContext fromContext =
        producer.createConfigurationFromContext(ConfigurationContext.getFromContext(context, ActionPlaces.UNKNOWN));
      assertEquals("MyTest.m.nodeId", fromContext.getConfiguration().getName());
    });
  }

  /**
   * A plain {@code @Test} method under one invocation of a {@code @ParameterizedClass}.
   * The method node points at the method, and its parent invocation node points at the class.
   * Only the invocation's own repetition of the class location distinguishes this from a plain {@code Class,method} run.
   */
  @Test
  public void testMethodUnderParameterizedClassInvocation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    myFixture.addClass("class MyTest {@org.junit.jupiter.api.Test void m() {}}");

    ReadAction.runBlocking(() -> {
      SMTestProxy classNode = withLocator(new SMTestProxy("MyTest", true, "java:suite://MyTest"));
      SMTestProxy invocation = withLocator(new SMTestProxy("[1] a", true, "java:suite://MyTest"));
      SMTestProxy method = withLocator(new SMTestProxy("m()", false, "java:test://MyTest/m"));
      classNode.addChild(invocation);
      invocation.addChild(method);
      String nodeId = "[engine:junit-jupiter]/[class-template:MyTest]/[class-template-invocation:#1]/[method:m()]";
      method.putUserData(SMTestProxy.NODE_ID, nodeId);

      assertEquals(nodeId, TestUniqueId.getEffectiveNodeId(method, project, GlobalSearchScope.projectScope(project)));
    });
  }

  /** The same method without the invocation node above it is still rerun as {@code Class,method}. */
  @Test
  public void testPlainMethodHasNoEffectiveNodeId() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    myFixture.addClass("class MyTest {@org.junit.jupiter.api.Test void m() {}}");

    ReadAction.runBlocking(() -> {
      SMTestProxy classNode = withLocator(new SMTestProxy("MyTest", true, "java:suite://MyTest"));
      SMTestProxy method = withLocator(new SMTestProxy("m()", false, "java:test://MyTest/m"));
      classNode.addChild(method);
      method.putUserData(SMTestProxy.NODE_ID, "[engine:junit-jupiter]/[class:MyTest]/[method:m()]");

      assertNull(TestUniqueId.getEffectiveNodeId(method, project, GlobalSearchScope.projectScope(project)));
    });
  }

  private static SMTestProxy withLocator(SMTestProxy proxy) {
    proxy.setLocator(JavaTestLocator.INSTANCE);
    return proxy;
  }

  @Test
  public void testCustomEngineId() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    PsiClass psiClass = myFixture.addClass("""
                                             /** @noinspection ALL*/ @org.junit.platform.commons.annotation.Testable
                                             class MyTest {
                                               public Object testScenario = null;
                                             }""");

    ReadAction.runBlocking(() -> {
      SMTestProxy parent = new SMTestProxy("MyTest", true, "java:suite://MyTest");
      SMTestProxy proxy = new SMTestProxy("nodeId", false, "file://" + psiClass.getContainingFile().getVirtualFile().getPath() + ":3");
      proxy.setLocator(FileUrlProvider.INSTANCE);
      parent.addChild(proxy);
      proxy.putUserData(SMTestProxy.NODE_ID, "nodeId");
      assertEquals("nodeId", TestUniqueId.getEffectiveNodeId(proxy, project, GlobalSearchScope.projectScope(project)));
      RunConfigurationProducer<?> producer = RunConfigurationProducer.getInstance(UniqueIdConfigurationProducer.class);
      MapDataContext context = new MapDataContext();
      context.put(CommonDataKeys.PROJECT, project);
      context.put(AbstractTestProxy.DATA_KEYS, new AbstractTestProxy[]{proxy});
      context.put(AbstractTestProxy.DATA_KEY, proxy);
      context.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiClass.getFields()[0]));
      JUnitConfiguration oldConfiguration = new JUnitConfiguration("", project);
      oldConfiguration.setModule(myFixture.getModule());
      context.put(RunConfiguration.DATA_KEY, oldConfiguration);

      ConfigurationFromContext fromContext =
        producer.createConfigurationFromContext(ConfigurationContext.getFromContext(context, ActionPlaces.UNKNOWN));
      assertEquals("nodeId", fromContext.getConfiguration().getName());
    });
  }
}
