// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.navigation;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.idea.devkit.navigation.ExtensionPluginDescriptorDeclarationFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.navigation.ExtensionPluginDescriptorDeclarationFixtureKt.extensionPluginDescriptorDeclarationFixture;

@SuppressWarnings("NewClassNamingConvention")
@TestDataPath("/navigation/extensionDeclaration")
@TestApplication
public class KtExtensionPluginDescriptorDeclarationRelatedItemLineMarkerProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<ExtensionPluginDescriptorDeclarationFixture> extensionDeclaration =
    extensionPluginDescriptorDeclarationFixture(projectFixture, DevkitKtTestsUtil.TESTDATA_PATH + "navigation/extensionDeclaration");

  @Test
  public void testInvalidKtExtension() {
    extensionDeclaration.get().doTestInvalidExtension("MyInvalidKtExtension.kt");
  }

  @Test
  public void testKtExtension() {
    extensionDeclaration.get().doTestExtension("MyKtExtension.kt", "<myEp implementation=\"MyKtExtension\"/>");
  }
}
