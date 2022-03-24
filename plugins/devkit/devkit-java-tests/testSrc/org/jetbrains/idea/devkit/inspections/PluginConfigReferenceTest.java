// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.diagnostic.ITNReporter;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.nio.file.Paths;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/references/pluginConfigReference")
public class PluginConfigReferenceTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "references/pluginConfigReference";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("platform-impl", PathUtil.getJarPathForClass(ITNReporter.class));
    moduleBuilder.addLibrary("platform-rt", PathUtil.getJarPathForClass(IncorrectOperationException.class));
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(Iconable.class));
    moduleBuilder.addLibrary("platform-analysis", PathUtil.getJarPathForClass(LocalInspectionEP.class));
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP.class))
      .resolveSibling("intellij.platform.resources").toString());
    moduleBuilder.addLibrary("ide-core", PathUtil.getJarPathForClass(Configurable.class));
    moduleBuilder.addLibrary("editor-ui-api", PathUtil.getJarPathForClass(AdvancedSettings.class));
  }

  public void testRegistryKeyIdHighlighting() {
    doHighlightingTest("RegistryKeyId.java",
                       "registryKeyId.xml");
  }

  public void testRegistryKeyIdCompletion() {
    final List<String> variants = myFixture.getCompletionVariants("RegistryKeyIdCompletion.java",
                                                                  "registryKeyId.xml");
    assertContainsElements(variants,
                           "my.plugin.key",
                           "vcs.showConsole");

    final LookupElementPresentation plugin = getLookupElementPresentation("my.plugin.key");
    assertEquals(" My description", plugin.getTailText());
    assertEquals("myDefaultValue", plugin.getTypeText());
    assertEquals(AllIcons.Nodes.PluginRestart, plugin.getIcon());

    final LookupElementPresentation properties = getLookupElementPresentation("vcs.showConsole");
    assertEquals(" Show 'Console' tab in VCS toolwindow that logs all write-commands performed by IDE.", properties.getTailText());
    assertEquals("true", properties.getTypeText());
    assertEquals(AllIcons.Nodes.Plugin, properties.getIcon());
  }

  public void testExperimentalFeatureIdHighlighting() {
    doHighlightingTest("ExperimentalFeatureId.java",
                       "experimentalFeatureId.xml");
  }

  public void testExperimentalFeatureIdCompletion() {
    final List<String> variants = myFixture.getCompletionVariants("ExperimentalFeatureIdCompletion.java",
                                                                  "experimentalFeatureId.xml");
    assertContainsElements(variants,
                           "my.feature.id",
                           "my.internal.feature.id");

    final LookupElementPresentation feature = getLookupElementPresentation("my.feature.id");
    assertFalse(feature.isItemTextBold());
    assertEquals(" Feature ID Description", feature.getTailText());
    assertEquals("100%", feature.getTypeText());
    assertEquals(AllIcons.Nodes.PluginRestart, feature.getIcon());

    final LookupElementPresentation internal = getLookupElementPresentation("my.internal.feature.id");
    assertTrue(internal.isItemTextBold());
    assertEquals(" No Description", internal.getTailText());
    assertEquals("", internal.getTypeText());
    assertEquals(AllIcons.Nodes.Plugin, internal.getIcon());
  }

  public void testAdvancedSettingsIdHighlighting() {
    doHighlightingTest("AdvancedSettingsId.java",
                       "advancedSettingsId.xml");
  }

  public void testAdvancedSettingsIdCompletion() {
    final List<String> variants = myFixture.getCompletionVariants("AdvancedSettingsIdCompletion.java",
                                                                  "advancedSettingsId.xml");
    assertContainsElements(variants,
                           "advancedSettingId",
                           "advancedSettingId2");

    final LookupElementPresentation setting = getLookupElementPresentation("advancedSettingId");
    assertEquals("defaultValue", setting.getTypeText());
    assertEquals(AllIcons.General.Settings, setting.getIcon());
  }

  public void testNotificationGroupIdHighlighting() {
    doHighlightingTest("NotificationGroupId.java",
                       "notificationGroupId.xml");
  }

  public void testNotificationGroupIdCompletion() {
    final List<String> variants = myFixture.getCompletionVariants("NotificationGroupIdCompletion.java",
                                                                  "notificationGroupId.xml");
    assertContainsElements(variants,
                           "my.balloon",
                           "my.toolwindow");

    final LookupElementPresentation balloon = getLookupElementPresentation("my.balloon");
    assertNull(balloon.getTailText());
    assertEquals("BALLOON", balloon.getTypeText());
    assertEquals(AllIcons.Ide.Notification.NoEvents, balloon.getTypeIcon());

    final LookupElementPresentation toolwindow = getLookupElementPresentation("my.toolwindow");
    assertEquals(" (my.toolwindow.id)", toolwindow.getTailText());
    assertEquals("TOOL_WINDOW", toolwindow.getTypeText());
    assertNull(toolwindow.getTypeIcon());
  }

  private void doHighlightingTest(String... filePaths) {
    myFixture.enableInspections(new UnresolvedPluginConfigReferenceInspection());
    myFixture.testHighlighting(filePaths);
  }

  private LookupElementPresentation getLookupElementPresentation(String lookupString) {
    final LookupElement lookupElement = ContainerUtil.find(myFixture.getLookupElements(),
                                                           element -> element.getLookupString().equals(lookupString));
    assertNotNull(lookupString, lookupElement);
    return LookupElementPresentation.renderElement(lookupElement);
  }
}
