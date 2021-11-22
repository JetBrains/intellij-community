// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.idea.devkit.inspections.internal.StatisticsCollectorNotRegisteredInspection;

public abstract class StatisticsCollectorNotRegisteredInspectionTestBase extends PluginModuleTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.internal.statistic.service.fus.collectors; " +
                       "public interface FeatureUsagesCollector {}");
    myFixture.addClass("package com.intellij.internal.statistic.service.fus.collectors; " +
                       "public abstract class CounterUsagesCollector implements FeatureUsagesCollector {}");
    myFixture.addClass("package com.intellij.internal.statistic.service.fus.collectors; " +
                       "public abstract class ProjectUsagesCollector implements FeatureUsagesCollector {}");

    myFixture.enableInspections(new StatisticsCollectorNotRegisteredInspection());
  }

  protected abstract String getSourceFileExtension();

  public void testRegisteredStatisticsCollector() {
    setPluginXml("registeredCounterCollector-plugin.xml");
    myFixture.testHighlighting("RegisteredCounterCollector." + getSourceFileExtension());
  }

  public void testRegisteredNestedStatisticsCollector() {
    setPluginXml("registeredNestedCounterCollector-plugin.xml");
    myFixture.testHighlighting("RegisteredNestedCounterCollector." + getSourceFileExtension());
  }

  public void testUnregisteredCounterCollector() {
    setPluginXml("unregisteredCounterCollector-plugin.xml");
    myFixture.testHighlighting("UnregisteredCounterCollector." + getSourceFileExtension());
    IntentionAction registerAction = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(registerAction);

    myFixture.checkResultByFile("META-INF/plugin.xml", "unregisteredCounterCollector-plugin_after.xml", true);
  }

  public void testUnregisteredProjectCollector() {
    setPluginXml("unregisteredProjectCollector-plugin.xml");
    myFixture.testHighlighting("UnregisteredProjectCollector." + getSourceFileExtension());
    IntentionAction registerAction = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(registerAction);

    myFixture.checkResultByFile("META-INF/plugin.xml", "unregisteredProjectCollector-plugin_after.xml", true);
  }

  public void testUnregisteredNestedCountCollector() {
    setPluginXml("unregisteredCounterCollector-plugin.xml");
    myFixture.testHighlighting("UnregisteredNestedCounterCollector." + getSourceFileExtension());
    IntentionAction registerAction = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(registerAction);

    myFixture.checkResultByFile("META-INF/plugin.xml", "unregisteredNestedCounterCollector-plugin_after.xml", true);
  }
}
