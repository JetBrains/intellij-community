// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import org.jetbrains.annotations.NonNls
import java.nio.file.Files
import java.nio.file.Path

class TestGradleSettingsScriptHelper(val root: Path, val subprojects: Array<@NonNls String> = arrayOf()) {
  fun build(): String {
    subprojects.forEach {
      Files.createDirectories(root.resolve(it.replace(":", "/").trim('/')))
    }
    return subprojects.joinToString(prefix = "\n", separator = "\n", postfix = "\n") { "include '$it'" }
  }
}