// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jdom.Element;

public class ProductivityFeaturesTest extends LightPlatformTestCase {
  private static final String XML_FILE = "TestProductivityFeatures.xml";
  private static final String XML_FEATURE_ID = "testXmlFeature";
  private static final String XML_GROUP_ID = "testXmlGroup";
  private static final String XML_ACTION_ID = "TestXmlFeatureAction";
  private static final String PLATFORM_FEATURE_ID = "SearchEverywhere";

  private ProductivityFeaturesRegistry myRegistry;
  private FeatureUsageTracker myTracker;
  private Disposable myBeanDisposable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRegistry = ProductivityFeaturesRegistry.getInstance();
    ((ProductivityFeaturesRegistryImpl)myRegistry).prepareForTest();
    myTracker = FeatureUsageTracker.getInstance();

    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ProductivityFeaturesProvider.EP_NAME, new TestProductivityFeatureProvider(), getTestRootDisposable());

    myBeanDisposable = Disposer.newDisposable(getTestRootDisposable(), "productivityFeatures bean");
    registerBean(XML_FILE, myBeanDisposable);

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
      myBeanDisposable = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private static void registerBean(String file, Disposable parentDisposable) {
    ProductivityFeaturesBean bean = new ProductivityFeaturesBean();
    bean.file = file;
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ProductivityFeaturesBean.EP_NAME, bean, parentDisposable);
  }

  public void testXmlFeatureLoadedFromBean() {
    FeatureDescriptor featureDescriptor = myRegistry.getFeatureDescriptor(XML_FEATURE_ID);
    assertNotNull("all features: " + myRegistry.getFeatureIds(), featureDescriptor);
    assertEquals(XML_GROUP_ID, featureDescriptor.getGroupId());
    assertNull(featureDescriptor.getProvider());
    assertNotNull(myRegistry.getGroupDescriptor(XML_GROUP_ID));
    assertSame(featureDescriptor, myRegistry.findFeatureByAction(XML_ACTION_ID));
  }

  public void testXmlFeatureLoadedFromBeanWithLeadingSlash() {
    Disposer.dispose(myBeanDisposable);
    registerBean("/" + XML_FILE, getTestRootDisposable());
    ((ProductivityFeaturesRegistryImpl)myRegistry).prepareForTest();

    assertNotNull(myRegistry.getFeatureDescriptor(XML_FEATURE_ID));
  }

  public void testXmlFeatureRemovedWithBean() {
    assertNotNull(myRegistry.getFeatureDescriptor(XML_FEATURE_ID));

    Disposer.dispose(myBeanDisposable);

    assertNull(myRegistry.getFeatureDescriptor(XML_FEATURE_ID));
    assertNull(myRegistry.findFeatureByAction(XML_ACTION_ID));
    assertNotNull(myRegistry.getFeatureDescriptor(TestProductivityFeatureProvider.tipId));
    assertNotNull(myRegistry.getFeatureDescriptor(PLATFORM_FEATURE_ID));
  }

  public void testPlatformFeaturesLoadedFromBean() {
    FeatureDescriptor featureDescriptor = myRegistry.getFeatureDescriptor(PLATFORM_FEATURE_ID);
    assertNotNull("all features: " + myRegistry.getFeatureIds(), featureDescriptor);
    assertNull(featureDescriptor.getProvider());
    assertSame(featureDescriptor, myRegistry.findFeatureByAction(PLATFORM_FEATURE_ID));
  }

  public void testPrepareForTestReloadsFromExtensionPoints() {
    assertNotNull(myRegistry.getFeatureDescriptor(XML_FEATURE_ID));

    ((ProductivityFeaturesRegistryImpl)myRegistry).prepareForTest();
    assertNotNull(myRegistry.getFeatureDescriptor(XML_FEATURE_ID));
    assertSame(myRegistry.getFeatureDescriptor(XML_FEATURE_ID), myRegistry.findFeatureByAction(XML_ACTION_ID));

    Disposer.dispose(myBeanDisposable);
    assertNull(myRegistry.getFeatureDescriptor(XML_FEATURE_ID));
    assertNull(myRegistry.findFeatureByAction(XML_ACTION_ID));
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
