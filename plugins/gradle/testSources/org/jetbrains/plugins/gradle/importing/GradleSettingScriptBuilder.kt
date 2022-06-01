// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import java.util.*
import kotlin.collections.ArrayList

class GradleSettingScriptBuilder {

  private val lines = ArrayList<String>()

  private var projectName: String? = null

  fun setProjectName(projectName: String) {
    this.projectName = projectName
  }

  fun include(name: String) = apply {
    lines.add("include '$name'")
  }

  fun includeBuild(name: String) = apply {
    lines.add("includeBuild '$name'")
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