// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.platform.testFramework.monorepo.MonorepoProjectStructure.isFromCommunity
import org.jetbrains.jps.model.module.JpsModule

internal object PlatformApi {

  private val excludeModuleNames = setOf(
    "intellij.platform.buildScripts", // build scripts
    "intellij.platform.testFramework",
    "intellij.platform.testExtensions",
    "intellij.platform.uast", // IDEA
    "intellij.platform.uast.ide", // IDEA
    "intellij.platform.images", // plugin
    "intellij.platform.images.copyright", // plugin
    "intellij.platform.images.build", // build scripts
  )

  private val excludeModuleNamePrefixes = setOf(
    "intellij.platform.testFramework.",
    "intellij.platform.buildScripts.",
  )

  private val excludeModuleNameSuffixes = setOf(
    ".testFramework",
    ".performanceTesting"
  )

  fun JpsModule.isPlatformModule(): Boolean {
    val name = name
    return name.startsWith("intellij.platform.") &&
           name !in excludeModuleNames &&
           excludeModuleNamePrefixes.none(name::startsWith) &&
           excludeModuleNameSuffixes.none(name::endsWith) &&
           isFromCommunity()
  }
}