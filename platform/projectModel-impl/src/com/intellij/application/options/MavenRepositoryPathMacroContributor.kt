// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import java.nio.file.Files
import java.nio.file.Path

private const val MAVEN_REPOSITORY = "MAVEN_REPOSITORY"
private const val MAVEN_REPO_LOCAL_PROPERTY = "maven.repo.local"

/**
 * Defines the `MAVEN_REPOSITORY` path macro from the local Maven configuration.
 */
internal class MavenRepositoryPathMacroContributor : NamedPathMacroContributor {
  override val macroNames: Collection<String>
    get() = listOf(MAVEN_REPOSITORY)

  override fun registerPathMacros(macros: MutableMap<String, String>, legacyMacros: MutableMap<String, String>) {
    macros.put(MAVEN_REPOSITORY, getRepositoryPath())
  }

  override fun forceRegisterPathMacros(macros: MutableMap<String, String>) {
    val repository = System.getProperty(MAVEN_REPO_LOCAL_PROPERTY)
    if (repository != null) {
      macros.put(MAVEN_REPOSITORY, repository)
    }
  }
}

/**
 * Keeps the resolution order of the removed Maven plugin contributor.
 * Do not read `MAVEN_OPTS` or the global settings.xml here: a changed macro value reloads every library of a project.
 */
private fun getRepositoryPath(): String {
  System.getProperty(MAVEN_REPO_LOCAL_PROPERTY)?.let { return it }
  val userSettings = JpsMavenSettings.getUserMavenSettingsXml()
  if (Files.exists(userSettings)) {
    val fromSettings = JpsMavenSettings.getRepositoryFromSettings(userSettings)
    if (!fromSettings.isNullOrEmpty() && !fromSettings.contains("\${")) {
      return Path.of(fromSettings).toString()
    }
  }
  return Path.of(SystemProperties.getUserHome(), ".m2", "repository").toString()
}
