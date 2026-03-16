// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.Collections

/*
 * Sort the files heuristically to load the plugin jar containing plugin descriptors without extra ZipFile accesses.
 * File name preference:
 * a) last order for files with resources in name, like resources_en.jar
 * b) last order for files that have `-digit` suffix is the name e.g., completion-ranking.jar is before `gson-2.8.0.jar` or `junit-m5.jar`
 * c) JAR with name close to plugin's directory name, e.g., kotlin-XXX.jar is before all-open-XXX.jar
 * d) Shorter name, e.g., android.jar is before android-base-common.jar
 */
@ApiStatus.Internal
// it is in util module to be able to reuse in build scripts
fun putMoreLikelyPluginJarsFirst(pluginDirName: String, filesInLibUnderPluginDir: MutableList<Path>) {
  // don't use kotlin sortWith to avoid loading of CollectionsKt
  Collections.sort(filesInLibUnderPluginDir, Comparator { o1, o2 ->
    val o2Name = o2.fileName.toString()
    val o1Name = o1.fileName.toString()
    val o2StartsWithResources = o2Name.startsWith("resources")
    val o1StartsWithResources = o1Name.startsWith("resources")
    if (o2StartsWithResources != o1StartsWithResources) {
      return@Comparator if (o2StartsWithResources) -1 else 1
    }

    val o2IsVersioned = fileNameIsLikeVersionedLibraryName(o2Name)
    val o1IsVersioned = fileNameIsLikeVersionedLibraryName(o1Name)
    if (o2IsVersioned != o1IsVersioned) {
      return@Comparator if (o2IsVersioned) -1 else 1
    }

    val o2StartsWithNeededName = o2Name.startsWith(pluginDirName, ignoreCase = true)
    val o1StartsWithNeededName = o1Name.startsWith(pluginDirName, ignoreCase = true)
    if (o2StartsWithNeededName != o1StartsWithNeededName) {
      return@Comparator if (o2StartsWithNeededName) 1 else -1
    }

    val o2EndsWithIdea = o2Name.endsWith("-idea.jar")
    val o1EndsWithIdea = o1Name.endsWith("-idea.jar")
    if (o2EndsWithIdea != o1EndsWithIdea) {
      return@Comparator if (o2EndsWithIdea) 1 else -1
    }

    val o2IsDbPlugin = o2Name == "database-plugin.jar"
    val o1IsDbPlugin = o1Name == "database-plugin.jar"
    if (o2IsDbPlugin != o1IsDbPlugin) {
      return@Comparator if (o2IsDbPlugin) 1 else -1
    }
    o1Name.length - o2Name.length
  })
}

private fun fileNameIsLikeVersionedLibraryName(name: String): Boolean {
  val i = name.lastIndexOf('-')
  if (i == -1) {
    return false
  }

  if (i + 1 < name.length) {
    val c = name[i + 1]
    return Character.isDigit(c) || ((c == 'm' || c == 'M') && i + 2 < name.length && Character.isDigit(name[i + 2]))
  }
  return false
}