/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredFixtureKt.componentNotRegisteredFixture;
import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/componentNotRegistered")
@TestApplication
public class ComponentNotRegisteredInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<ComponentNotRegisteredFixture> componentNotRegistered =
    componentNotRegisteredFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/componentNotRegistered");

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