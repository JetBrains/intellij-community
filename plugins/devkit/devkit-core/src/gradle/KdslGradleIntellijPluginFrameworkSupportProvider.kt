// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.gradle

import org.jetbrains.plugins.gradle.frameworkSupport.BuildScriptDataBuilder

class KdslGradleIntellijPluginFrameworkSupportProvider : GradleIntellijPluginFrameworkSupportProvider() {

  override fun configureBuildScript(buildScriptData: BuildScriptDataBuilder, pluginVersion: String?, ideVersion: String?) {
    buildScriptData
      .addPluginDefinitionInPluginsGroup("""
        id("org.jetbrains.intellij") version "${pluginVersion.orEmpty()}"
      """.trimIndent())
      .addOther(HELP_COMMENT + """
        intellij {
            version.set("${ideVersion.orEmpty()}")
        }
      """.trimIndent())
      .addOther("""
        tasks.getByName<org.jetbrains.intellij.tasks.PatchPluginXmlTask>("patchPluginXml") {
            changeNotes.set(""${'"'}
                Add change notes here.<br>
                <em>most HTML tags may be used</em>
            ""${'"'}.trimIndent())
        }
      """.trimIndent())
  }
}
