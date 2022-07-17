// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import java.util.*

class GradleSettingScriptBuilder {

  private val lines = ArrayList<String>()

  private var projectName: String? = null

  fun setProjectName(projectName: String) {
    this.projectName = projectName
  }

  fun enableFeaturePreview(featureName: String) {
    lines.add("enableFeaturePreview('$featureName')")
  }

  fun include(name: String) = apply {
    lines.add("include '$name'")
  }

  fun includeBuild(name: String) = apply {
    lines.add("includeBuild '$name'")
  }

  fun raw(content: String) {
    lines.addAll(content.split('\n'))
  }

  fun generate(): String {
    val joiner = StringJoiner("\n")
    if (projectName != null) {
      joiner.add("rootProject.name = '$projectName'")
    }
    if (projectName != null && lines.isNotEmpty()) {
      joiner.add("")
    }
    for (line in lines) {
      joiner.add(line)
    }
    return joiner.toString()
  }
}