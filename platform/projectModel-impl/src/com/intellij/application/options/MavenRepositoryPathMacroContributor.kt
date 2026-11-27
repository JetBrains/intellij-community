// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import org.jetbrains.jps.model.serialization.JpsMavenSettings

private const val MAVEN_REPOSITORY = "MAVEN_REPOSITORY"
private const val MAVEN_REPO_LOCAL_PROPERTY = "maven.repo.local"

/**
 * Defines the `MAVEN_REPOSITORY` path macro from the local Maven configuration.
 * The JPS build resolves the macro with the same [JpsMavenSettings] logic.
 */
internal class MavenRepositoryPathMacroContributor : NamedPathMacroContributor {
  override val macroNames: Collection<String>
    get() = listOf(MAVEN_REPOSITORY)

  override fun registerPathMacros(macros: MutableMap<String, String>, legacyMacros: MutableMap<String, String>) {
    macros.put(MAVEN_REPOSITORY, JpsMavenSettings.getMavenRepositoryPath())
  }

  override fun forceRegisterPathMacros(macros: MutableMap<String, String>) {
    val repository = System.getProperty(MAVEN_REPO_LOCAL_PROPERTY)
    if (repository != null) {
      macros.put(MAVEN_REPOSITORY, repository)
    }
  }
}
