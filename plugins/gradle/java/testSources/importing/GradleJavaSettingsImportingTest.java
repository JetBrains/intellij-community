// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import org.junit.Test;

/**
 * @author Vladislav.Soroka
 */
public class GradleJavaSettingsImportingTest extends GradleSettingsImportingTest {
  @Test
  public void testCompilerConfigurationSettingsImport() throws Exception {

    importProject(
      withGradleIdeaExtPlugin(
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
        "}")
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
}
