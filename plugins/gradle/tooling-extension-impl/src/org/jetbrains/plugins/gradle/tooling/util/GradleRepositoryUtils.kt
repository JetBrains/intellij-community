// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleRepositoryUtils")

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.UrlArtifactRepository

sealed interface DeclaredRepository {
  val name: String
}

class DeclaredRepositoryImpl(override val name: String) : DeclaredRepository

class FileRepository(override val name: String, val files: List<String>) : DeclaredRepository

class UrlRepository(
  override val name: String,
  val url: String,
  val type: UrlRepositoryType,
) : DeclaredRepository

enum class UrlRepositoryType {
  MAVEN, IVY, OTHER
}

fun getDeclaredRepositories(project: Project): List<DeclaredRepository> {
  return project.repositories
    .map {
      if (it is UrlArtifactRepository) {
        val type = when (it) {
          is MavenArtifactRepository -> UrlRepositoryType.MAVEN
          is IvyArtifactRepository -> UrlRepositoryType.IVY
          else -> UrlRepositoryType.OTHER
        }
        return@map UrlRepository(it.name, it.url.toString(), type)
      }
      if (it is FlatDirectoryArtifactRepository) {
        return@map FileRepository(it.name, it.dirs.map { file -> file.path })
      }
      return@map DeclaredRepositoryImpl(it.name)
    }
    .toList()
}

fun isIvyRepositoryUsed(project: Project): Boolean {
  return project.repositories
    .any { it is IvyArtifactRepository }
}
