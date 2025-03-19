// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.diagnostic.ITNReporter;
import com.intellij.notification.impl.NotificationGroupEP;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.inspections.UnresolvedPluginConfigReferenceInspection;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/references/pluginConfigReference")
public class KtPluginConfigReferenceTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "references/pluginConfigReference";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addLibrary("platform-core", PathUtil.getJarPathForClass(RegistryManager.class));
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("platform-ide-impl", PathUtil.getJarPathForClass(ITNReporter.class));
    moduleBuilder.addLibrary("platform-util-base", PathUtil.getJarPathForClass(IncorrectOperationException.class));
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(Iconable.class));
    moduleBuilder.addLibrary("platform-analysis", PathUtil.getJarPathForClass(LocalInspectionEP.class));
    moduleBuilder.addLibrary("platform-resources", PathManager.getResourceRoot(LocalInspectionEP.class, "/defaultFileTypes.xml"));
    moduleBuilder.addLibrary("platform-ide-core", PathUtil.getJarPathForClass(Configurable.class));
    moduleBuilder.addLibrary("platform-ide-core-impl", PathUtil.getJarPathForClass(NotificationGroupEP.class));
    moduleBuilder.addLibrary("platform-editor", PathUtil.getJarPathForClass(AdvancedSettings.class));
    moduleBuilder.addLibrary("platform-extensions", PathUtil.getJarPathForClass(BaseExtensionPointName.class));
  }

  public void testExtensionPointHighlighting() {
    doHighlightingTest("ExtensionPointReference.kt",
                       "extensionPointReference.xml");
  }

  private void doHighlightingTest(String... filePaths) {
    myFixture.enableInspections(new UnresolvedPluginConfigReferenceInspection());
    myFixture.testHighlighting(filePaths);
  }
}
