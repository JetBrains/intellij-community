// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.gradle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.frameworkSupport.BuildScriptDataBuilder;

public class KdslGradleIntellijPluginFrameworkSupportProvider extends GradleIntellijPluginFrameworkSupportProvider {
  @Override
  protected void configureBuildScript(@NotNull BuildScriptDataBuilder buildScriptData, String pluginVersion, String ideVersion) {
    buildScriptData
      .addPluginDefinitionInPluginsGroup("id(\"org.jetbrains.intellij\") version \"" + pluginVersion + "\"")
      .addOther(HELP_COMMENT +
                "intellij {\n    version = \"" + ideVersion + "\"\n}\n")
      .addOther("tasks.getByName<org.jetbrains.intellij.tasks.PatchPluginXmlTask>(\"patchPluginXml\") {\n" +
                "    changeNotes(\"\"\"\n" +
                "      Add change notes here.<br>\n" +
                "      <em>most HTML tags may be used</em>\"\"\")\n" +
                "}");
  }
}
