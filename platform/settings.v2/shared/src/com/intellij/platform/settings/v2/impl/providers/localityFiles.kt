// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md
// @spec ../../../../../../../../../spec/layer-locality.spec.md

import com.intellij.platform.settings.v2.LayerLocality

/**
 * Holds the base name of the settings v2 config files on disk. A project keeps its config in a directory of this
 * name, `<basePath>/.jetbrains`, and a directory keeps its config in `.jetbrains.json` and its locality files.
 */
const val JETBRAINS_CONFIG_NAME: String = ".jetbrains"

/**
 * Returns the on-disk file name of the [locality] variant of the team file [teamFileName]. The team layer keeps the
 * plain name. The local and the environmental layers insert an infix before the extension. So `.jetbrains.json`
 * gives `.jetbrains.local.json` and `.jetbrains.env.json`, and `project.settings.json` gives
 * `project.settings.local.json` and `project.settings.env.json` (ADR 0075).
 */
fun localityFileName(teamFileName: String, locality: LayerLocality): String = when (locality) {
  LayerLocality.TEAM -> teamFileName
  LayerLocality.LOCAL -> teamFileName.insertBeforeExtension("local")
  LayerLocality.ENVIRONMENTAL -> teamFileName.insertBeforeExtension("env")
}

private fun String.insertBeforeExtension(infix: String): String {
  val dot = lastIndexOf('.')
  return if (dot < 0) "$this.$infix" else "${substring(0, dot)}.$infix${substring(dot)}"
}
