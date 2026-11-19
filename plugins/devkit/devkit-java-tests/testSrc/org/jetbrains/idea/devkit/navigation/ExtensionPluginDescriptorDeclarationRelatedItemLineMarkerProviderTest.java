// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.navigation.ExtensionPluginDescriptorDeclarationFixtureKt.extensionPluginDescriptorDeclarationFixture;

@SuppressWarnings("NewClassNamingConvention")
@TestDataPath("$CONTENT_ROOT/testData/navigation/extensionDeclaration")
@TestApplication
public class ExtensionPluginDescriptorDeclarationRelatedItemLineMarkerProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<ExtensionPluginDescriptorDeclarationFixture> extensionDeclaration =
    extensionPluginDescriptorDeclarationFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/extensionDeclaration");

  @Test
  public void testInvalidExtension() {
    extensionDeclaration.get().doTestInvalidExtension("MyInvalidExtension.java");
  }

  @Test
  public void testExtensionNoParameters() {
    extensionDeclaration.get().doTestExtension("MyExtensionNoParameters.java", "<myEp implementation=\"MyExtensionNoParameters\"/>");
  }

  @Test
  public void testExtensionSingleParameter() {
    extensionDeclaration.get().doTestExtension("MyExtensionSingleParameter.java", "<myEp implementation=\"MyExtensionSingleParameter\"/>");
  }

  @Test
  public void testExtensionTwoParameters() {
    extensionDeclaration.get().doTestExtension("MyExtensionTwoParameters.java", "<myEp implementation=\"MyExtensionTwoParameters\"/>");
  }

  @Test
  public void testNestedClassExtension() {
    extensionDeclaration.get().doTestExtension("MyNestedClassExtension.java", "<myEp implementation=\"MyNestedClassExtension$Nested\"/>");
  }
}
