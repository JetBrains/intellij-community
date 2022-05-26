// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.FileUrlProvider;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class JUnitUniqueIdTest extends LightJavaCodeInsightFixtureTestCase {
  public void testValidateUniqueId() {
    PsiFile file = myFixture.addFileToProject("some.txt", "");
    SMTestProxy proxy = new SMTestProxy("test1", false, "file://" + file.getVirtualFile().getPath());
    proxy.setLocator(new FileUrlProvider());
    proxy.putUserData(SMTestProxy.NODE_ID, "nodeId");
    assertEquals("nodeId", TestUniqueId.getEffectiveNodeId(proxy, getProject(), GlobalSearchScope.projectScope(getProject())));
  }

  public void testGeneratedName() {
    PsiClass psiClass = myFixture.addClass("class MyTest {void m() {}}");

    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    SMTestProxy parent = new SMTestProxy("MyTest", true, "java:suite://MyTest");
    SMTestProxy proxy = new SMTestProxy("nodeId", false, "java:test://MyTest.m");
    proxy.setLocator(JavaTestLocator.INSTANCE);
    parent.addChild(proxy);
    proxy.putUserData(SMTestProxy.NODE_ID, "nodeId");
    assertEquals("nodeId", TestUniqueId.getEffectiveNodeId(proxy, getProject(), GlobalSearchScope.projectScope(getProject())));
    RunConfigurationProducer<?> producer = RunConfigurationProducer.getInstance(UniqueIdConfigurationProducer.class);
    MapDataContext context = new MapDataContext();
    context.put(CommonDataKeys.PROJECT, getProject());
    context.put(AbstractTestProxy.DATA_KEYS, new AbstractTestProxy[]{proxy});
    context.put(AbstractTestProxy.DATA_KEY, proxy);
    context.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiClass.getMethods()[0]));
    JUnitConfiguration oldConfiguration = new JUnitConfiguration("", getProject());
    oldConfiguration.setModule(getModule());
    context.put(RunConfiguration.DATA_KEY, oldConfiguration);
    
    ConfigurationFromContext fromContext =
      producer.createConfigurationFromContext(ConfigurationContext.getFromContext(context, ActionPlaces.UNKNOWN));
    assertEquals("MyTest.m.nodeId", fromContext.getConfiguration().getName());
  }
}
