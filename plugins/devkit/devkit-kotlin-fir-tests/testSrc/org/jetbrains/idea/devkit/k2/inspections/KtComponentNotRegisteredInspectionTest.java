// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredFixtureKt.componentNotRegisteredFixture;
import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/componentNotRegistered")
@TestApplication
public class KtComponentNotRegisteredInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<ComponentNotRegisteredFixture> componentNotRegistered =
    componentNotRegisteredFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/componentNotRegistered");

  @Test
  public void testRegisteredAction() {
    componentNotRegistered.get().doTestRegisteredAction();
  }

  @Test
  public void testRegisteredActionInIDEAProject() {
    componentNotRegistered.get().doTestRegisteredActionInIDEAProject();
  }

  @Test
  public void testRegisteredActionInOptionalPluginDescriptor() {
    componentNotRegistered.get().doTestRegisteredActionInOptionalPluginDescriptor();
  }

  @Test
  public void testRegisteredInIncludedFileAction() {
    componentNotRegistered.get().doTestRegisteredInIncludedFileAction();
  }

  @Test
  public void testUnregisteredAction() {
    componentNotRegistered.get().doTestUnregisteredAction();
  }

  @Test
  public void testUnregisteredActionDeprecated() {
    componentNotRegistered.get().doTestUnregisteredActionDeprecated();
  }

  @Test
  public void testUnregisteredActionUsedViaConstructor() {
    componentNotRegistered.get().doTestUnregisteredActionUsedViaConstructor();
  }

  @Test
  public void testRegisteredApplicationComponent() {
    componentNotRegistered.get().doTestRegisteredApplicationComponent();
  }

  @Test
  public void testUnregisteredAbstractApplicationComponent() {
    componentNotRegistered.get().doTestUnregisteredAbstractApplicationComponent();
  }

  @Test
  public void testUnregisteredApplicationComponentWithoutPluginXml() {
    componentNotRegistered.get().doTestUnregisteredApplicationComponentWithoutPluginXml();
  }

  @Test
  public void testUnregisteredApplicationComponentWithRegisterFix() {
    componentNotRegistered.get().doTestUnregisteredApplicationComponentWithRegisterFix();
  }
}