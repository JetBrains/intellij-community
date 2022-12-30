// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.frameworkSupport.BuildScriptDataBuilder;

public class KdslGradleIntellijPluginFrameworkSupportProvider extends GradleIntellijPluginFrameworkSupportProvider {
  @Override
  protected void configureBuildScript(@NotNull BuildScriptDataBuilder buildScriptData, String pluginVersion, String ideVersion) {
    buildScriptData
      .addPluginDefinitionInPluginsGroup("id(\"org.jetbrains.intellij\") version \"" + pluginVersion + "\"")
      .addOther(HELP_COMMENT +
                "intellij {\n" +
                "    version.set(\"" + ideVersion + "\")\n" +
                "}\n")
      .addOther("""
                  tasks {
                      patchPluginXml {
                          changeNotes.set(""\"
                              Add change notes here.<br>
                              <em>most HTML tags may be used</em>        ""\".trimIndent())
                      }
                  }""");
  }
}
