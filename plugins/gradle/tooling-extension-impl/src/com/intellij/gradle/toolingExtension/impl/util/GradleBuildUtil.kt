// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.ArrayDeque

@Internal
object GradleBuildUtil {

  @JvmStatic
  fun getAllNestedBuilds(rootBuild: GradleBuild, gradleVersion: GradleVersion): Collection<GradleBuild> {
    assert(isValidBuildModel(rootBuild))
    val processedBuilds = HashSet<String>()
    val nestedBuilds = LinkedHashSet<GradleBuild>()
    val queue = ArrayDeque<GradleBuild>()
    processedBuilds.add(rootBuild.buildIdentifier.rootDir.path)
    queue.add(rootBuild)
    while (!queue.isEmpty()) {
      val build = queue.remove()
      for (nestedBuild in getEditableBuilds(build, gradleVersion)) {
        if (isValidBuildModel(nestedBuild)) {
          if (processedBuilds.add(nestedBuild.buildIdentifier.rootDir.path)) {
            nestedBuilds.add(nestedBuild)
            queue.add(nestedBuild)
          }
        }
      }
    }
    return nestedBuilds
  }

  /**
   * Get nested builds to be imported by IDEA.
   *
   * @return Before Gradle 8.0 - included builds, 8.0 and later - included and buildSrc builds.
   */
  private fun getEditableBuilds(build: GradleBuild, gradleVersion: GradleVersion): DomainObjectSet<out GradleBuild> {
    if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.0")) {
      return build.includedBuilds
    }
    val builds = build.editableBuilds
    return if (builds.isEmpty()) build.includedBuilds else builds
  }

  @JvmStatic
  fun isValidBuildModel(build: GradleBuild): Boolean {
    return build.buildIdentifier != null && build.rootProject != null
  }
}
