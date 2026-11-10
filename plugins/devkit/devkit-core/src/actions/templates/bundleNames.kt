// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.templates

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil

fun generateDefaultBundleName(module: Module): String {
  val nameWithoutPrefix = module.name.removePrefix("intellij.").removeSuffix(".impl")
  val commonGroupNames = listOf("platform", "vcs", "tools", "clouds")
  val commonPrefix = commonGroupNames.find { nameWithoutPrefix.startsWith("$it.") }
  val shortenedName = if (commonPrefix != null) nameWithoutPrefix.removePrefix("$commonPrefix.") else nameWithoutPrefix
  return shortenedName.split(".").joinToString("") { StringUtil.capitalize(it) } + "Bundle"
}
