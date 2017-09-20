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
package org.jetbrains.plugins.gradle.importing;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.service.project.manage.FacetHandlerExtension;
import com.intellij.openapi.externalSystem.service.project.manage.RunConfigHandlerExtension;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.*;

/**
 * Created by Nikita.Skvortsov
 * date: 18.09.2017.
 */
public class GradleSettingsImportingTest extends GradleImportingTestCase {


  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }


  @Test
  public void testInspectionSettingsImport() throws Exception {
    importProject(
      "buildscript {\n" +
      "  dependencies {\n" +
      "     classpath files('" + getGradlePluginPath() + "')\n" +
      "  }\n" +
      "}\n" +
      "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
      "idea {\n" +
      "  project.settings {\n" +
      "    inspections {\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );

    final InspectionProfileImpl profile = InspectionProfileManager.getInstance(myProject).getCurrentProfile();
    assertEquals("Gradle Imported", profile.getName());
  }


  @Test
  public void testCodeStyleSettingsImport() throws Exception {
    importProject(
      "buildscript {\n" +
      "  dependencies {\n" +
      "     classpath files('" + getGradlePluginPath() + "')\n" +
      "  }\n" +
      "}\n" +
      "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
      "idea {\n" +
      "  project.settings {\n" +
      "    codeStyle {\n" +
      "      indent 'tabs'\n" +
      "      indentSize 3\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );

    final CodeStyleScheme scheme = CodeStyleSchemes.getInstance().getCurrentScheme();
    final CodeStyleSettings settings = scheme.getCodeStyleSettings();

    assertEquals("Gradle Imported", scheme.getName());
    assertFalse(scheme.isDefault());

    assertTrue(settings.getIndentOptions().USE_TAB_CHARACTER);
    assertEquals(3, settings.getIndentOptions().INDENT_SIZE);
  }

  @Test
  public void testCompilerConfigurationSettingsImport() throws Exception {

    importProject(
      "buildscript {\n" +
      "  dependencies {\n" +
      "     classpath files('" + getGradlePluginPath() + "')\n" +
      "  }\n" +
      "}\n" +
      "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
      "idea {\n" +
      "  project.settings {\n" +
      "    compiler {\n" +
      "      resourcePatterns '!*.java;!*.class'\n" +
      "      clearOutputDirectory false\n" +
      "      addNotNullAssertions false\n" +
      "      autoShowFirstErrorInEditor false\n" +
      "      displayNotificationPopup false\n" +
      "      enableAutomake false\n" +
      "      parallelCompilation true\n" +
      "      rebuildModuleOnDependencyChange false\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );

    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);

    assertSameElements(compilerConfiguration.getResourceFilePatterns(), "!*.class", "!*.java");
    assertFalse(workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    assertFalse(compilerConfiguration.isAddNotNullAssertions());
    assertFalse(workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR);
    assertFalse(workspaceConfiguration.DISPLAY_NOTIFICATION_POPUP);
    assertFalse(workspaceConfiguration.MAKE_PROJECT_ON_SAVE);
    assertTrue(workspaceConfiguration.PARALLEL_COMPILATION);
    assertFalse(workspaceConfiguration.REBUILD_ON_DEPENDENCY_CHANGE);
  }

  @Test
  public void testApplicationRunConfigurationSettingsImport() throws Exception {
    final String typeName = "testRunConfig";

    TestRunConfigHandlerExtension testExtension = new TestRunConfigHandlerExtension(typeName);
    Extensions.getRootArea().getExtensionPoint(RunConfigHandlerExtension.EP_NAME).registerExtension(testExtension);

    importProject(
      "buildscript {\n" +
      "  dependencies {\n" +
      "     classpath files('" + getGradlePluginPath() + "')\n" +
      "  }\n" +
      "}\n" +
      "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
      "idea {\n" +
      "  module.settings {\n" +
      "    runConfigurations {\n" +
      "       app1 {\n" +
      "           type = '" + typeName + "'\n" +
      "           mainClass = 'my.app.Class'\n" +
      "           jvmArgs =   '-Xmx1g'\n" +
      "       }\n" +
      "       app2 {\n" +
      "           type = '" + typeName + "'\n" +
      "           mainClass = 'my.app.Class2'\n" +
      "       }\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );

    final Map<String, Map<String, Object>> configs = testExtension.getConfigs();

    assertContain(new ArrayList<>(configs.keySet()), "app1", "app2");
    Map<String, Object> app1Settings = configs.get("app1");
    Map<String, Object> app2Settings = configs.get("app2");

    assertEquals("my.app.Class", app1Settings.get("mainClass"));
    assertEquals("my.app.Class2", app2Settings.get("mainClass"));
    assertEquals("-Xmx1g", app1Settings.get("jvmArgs"));
    assertNull(app2Settings.get("jvmArgs"));
  }

  @Test
  public void testFacetSettingsImport() throws Exception {

    TestFacetHandlerExtension testExtension = new TestFacetHandlerExtension("testFacet");
    Extensions.getRootArea().getExtensionPoint(FacetHandlerExtension.EP_NAME).registerExtension(testExtension);


    importProject(
      "buildscript {\n" +
      "  dependencies {\n" +
      "     classpath files('" + getGradlePluginPath() + "')\n" +
      "  }\n" +
      "}\n" +
      "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
      "idea {\n" +
      "  module.settings {\n" +
      "    facets {\n" +
      "       testFacet {\n" +
      "           testField 'Test Value 1'\n" +
      "       }\n" +
      "       namedFacet {\n" +
      "           type 'testFacet'\n" +
      "           testField 'Test Value 2'\n" +
      "       }\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );

    final Map<String, Map<String, Object>> facetConfigs = testExtension.getConfigs();

    assertContain(new ArrayList<>(facetConfigs.keySet()), "testFacet", "namedFacet");
    Map<String, Object> unnamedSettings = facetConfigs.get("testFacet");
    Map<String, Object> namedSettings = facetConfigs.get("namedFacet");

    assertEquals("Test Value 1", unnamedSettings.get("testField"));
    assertEquals("Test Value 2", namedSettings.get("testField"));
  }

  private String getGradlePluginPath() {
    return getClass().getResource("/testCompilerConfigurationSettingsImport/gradle-idea-ext.jar").toString();
  }
}


class TestRunConfigHandlerExtension implements RunConfigHandlerExtension {

  private final String myTypeName;
  private final Map<String, Map<String, Object>> myConfigs = new HashMap<>();

  public TestRunConfigHandlerExtension(@NotNull String typeName) {
    myTypeName = typeName;
  }

  @Override
  public void process(@NotNull Project project, @NotNull String name, @NotNull Map<String, Object> cfg) {
    myConfigs.put(name, cfg);
  }

  @Override
  public void process(@NotNull Module module, @NotNull String name, @NotNull Map<String, Object> cfg) {
    myConfigs.put(name, cfg);
  }

  @Override
  public boolean canHandle(@NotNull String typeName) {
    return myTypeName.equals(typeName);
  }

  public Map<String, Map<String, Object>> getConfigs() {
    return myConfigs;
  }
}


class TestFacetHandlerExtension implements FacetHandlerExtension<Facet> {

  private final String myTypeName;

  private final Map<String, Map<String, Object>> myConfigs = new HashMap<>();

  TestFacetHandlerExtension(@NotNull String typeName) {
    myTypeName = typeName;
  }

  @NotNull
  @Override
  public Collection<Facet> process(@NotNull Module module, @NotNull String name, @NotNull Map<String, Object> cfg, @NotNull FacetManager facetManager) {
    myConfigs.put(name, cfg);
    return Collections.emptySet();
  }

  @Override
  public boolean canHandle(@NotNull String typeName) {
    return myTypeName.equals(typeName);
  }

  public Map<String, Map<String, Object>> getConfigs() {
    return myConfigs;
  }
}
