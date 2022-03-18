// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import java.util.*

class GradleSettingScriptBuilder(projectName: String) {

  private val builder = StringJoiner("\n")
    .add("rootProject.name = '$projectName'")
    .add("")

  fun generate() = builder.toString()

  fun include(name: String) = apply {
    builder.add("include '$name'")
  }

  fun includeBuild(name: String) = apply {
    builder.add("includeBuild '$name'")
  }

  companion object {
    @JvmStatic
    fun settingsScript(projectName: String, configure: GradleSettingScriptBuilder.() -> Unit) =
      GradleSettingScriptBuilder(projectName).apply(configure).generate()
  }
}