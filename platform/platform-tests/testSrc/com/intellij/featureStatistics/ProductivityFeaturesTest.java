// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jdom.Element;

public class ProductivityFeaturesTest extends LightPlatformTestCase {
  private ProductivityFeaturesRegistry myRegistry;
  private FeatureUsageTracker myTracker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRegistry = ProductivityFeaturesRegistry.getInstance();
    ((ProductivityFeaturesRegistryImpl)myRegistry).prepareForTest();
    myTracker = FeatureUsageTracker.getInstance();

    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ProductivityFeaturesProvider.EP_NAME, new TestProductivityFeatureProvider(), getTestRootDisposable());

    TipAndTrickBean tip = new TipAndTrickBean();
    tip.fileName = "TestTip.html";
    tip.setPluginDescriptor(PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID));
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), TipAndTrickBean.EP_NAME, tip, getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ((ProductivityFeaturesRegistryImpl)myRegistry).prepareForTest();
      myRegistry = null;
      myTracker = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testAddFeature(){
    TestProductivityFeatureProvider provider = ProductivityFeaturesProvider.EP_NAME.findExtension(TestProductivityFeatureProvider.class);

    final FeatureDescriptor featureDescriptor = myRegistry.getFeatureDescriptor(TestProductivityFeatureProvider.tipId);
    assertNotNull("App: "+ApplicationManager.getApplication()+"; provider: "+provider+
                  "; registry: "+myRegistry+";\n all features: "+myRegistry.getFeatureIds(), featureDescriptor);
    assertEquals(TestProductivityFeatureProvider.groupId, featureDescriptor.getGroupId());
  }

  public void testAddGroup(){
    final GroupDescriptor groupDescriptor = myRegistry.getGroupDescriptor(TestProductivityFeatureProvider.groupId);
    assertNotNull(groupDescriptor);
    assertEquals("test", groupDescriptor.getDisplayName());
  }

  public void testTriggerFeatureUsed(){
    myTracker.triggerFeatureUsed(TestProductivityFeatureProvider.tipId);
    final FeatureDescriptor featureDescriptor = myRegistry.getFeatureDescriptor(TestProductivityFeatureProvider.tipId);
    assertEquals(1, featureDescriptor.getUsageCount());
  }

  public void testTriggerFeatureShow(){
    myTracker.triggerFeatureShown(TestProductivityFeatureProvider.tipId);
    final FeatureDescriptor featureDescriptor = myRegistry.getFeatureDescriptor(TestProductivityFeatureProvider.tipId);
    assertEquals(1, featureDescriptor.getShownCount());
  }

  public void testStatistics(){
    long current = System.currentTimeMillis();
    myTracker.triggerFeatureUsed(TestProductivityFeatureProvider.tipId);
    final FeatureDescriptor featureDescriptor = myRegistry.getFeatureDescriptor(TestProductivityFeatureProvider.tipId);
    assertTrue(current <= featureDescriptor.getLastTimeUsed());
  }

  public void testStoredStatistics() {
    final FeatureDescriptor featureDescriptor = myRegistry.getFeatureDescriptor(TestProductivityFeatureProvider.tipId);
    Element featureStatistics = new Element("features");
    Element element = new Element("feature");
    element.setAttribute("id", TestProductivityFeatureProvider.tipId);
    element.setAttribute("count", "1");
    element.setAttribute("last-shown", String.valueOf(System.currentTimeMillis()));
    element.setAttribute("last-used", String.valueOf(System.currentTimeMillis()));
    element.setAttribute("average-frequency", "0");
    element.setAttribute("shown-count", "1");
    featureStatistics.addContent(element);
    ((FeatureUsageTrackerImpl)myTracker).loadState(featureStatistics);
    assertEquals(1, featureDescriptor.getUsageCount());
    assertEquals(1, featureDescriptor.getShownCount());
  }
}
