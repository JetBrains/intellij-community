// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.CodeGeneratorVersions
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PathUtil
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import java.nio.file.Path


@Service(Service.Level.PROJECT)
class CodegenJarLoader(val project: Project) {
  private val classLoaderToVersion = mutableMapOf<String, ClassLoader>()

  suspend fun getClassLoader(): ClassLoader {
    val artifactVersion = calculateArtifactVersion().getOrThrow()
    val result = classLoaderToVersion[artifactVersion]
    return if (result == null) {
      val classLoader = loadJar()
      classLoaderToVersion[artifactVersion] = classLoader
      classLoader
    }
    else {
      result
    }
  }

  private suspend fun loadJar(): ClassLoader {
    val artifactVersion = calculateArtifactVersion().getOrThrow()
    val codegenLibraryDescription = JpsMavenRepositoryLibraryDescriptor(GROUP_ID, ARTIFACT_ID, artifactVersion,
                                                                        true, emptyList())
    val roots =
      JarRepositoryManager.loadDependenciesAsync(project, codegenLibraryDescription, setOf(ArtifactKind.ARTIFACT),
                                                 listOf(INTELLIJ_DEPENDENCIES_DESCRIPTION),
                                                 null).await()

    val pathsToJars = roots.mapNotNull { root -> PathUtil.getLocalPath(root.file)?.let { Path.of(it) } }
    if (pathsToJars.isEmpty()) {
      error("Cannot get paths ${roots.joinToString(", ") { it.file.path }}. Maybe the new codegen version is not yet released?")
    }
    thisLogger().info("Path to jar: ${pathsToJars.joinToString(", ")}")
    return UrlClassLoader.build().files(pathsToJars).parent(this.javaClass.classLoader).get()
  }

  private suspend fun calculateArtifactVersion(): Result<String> {
    return withContext(Dispatchers.Default) {
      readAction {
        val allScope = GlobalSearchScope.allScope(project)
        val generatorVersionsClass = JavaPsiFacade.getInstance(project).findClass(CodeGeneratorVersions::class.java.name, allScope)
                                     ?: return@readAction Result.failure(
                                       RuntimeException(
                                         "Cannot find class CodeGeneratorVersions in source code. Probably issue with kotlin compilation caches."))
        thisLogger().debug("Reading generator version from: ${generatorVersionsClass.containingFile.containingDirectory}")
        val apiVersionField = generatorVersionsClass.findFieldByName("API_VERSION_INTERNAL", false)
                              ?: return@readAction Result.failure(
                                RuntimeException("Cannot find required field API_VERSION_INTERNAL in CodeGeneratorVersions class"))
        val apiVersion = (apiVersionField.initializer as? PsiLiteralExpression)?.value as? Int
                         ?: return@readAction Result.failure(RuntimeException("Cannot detect code generator API version by field"))
        val implMajorVersionField = generatorVersionsClass.findFieldByName("IMPL_MAJOR_VERSION_INTERNAL", false)
                                    ?: return@readAction Result.failure(
                                      RuntimeException(
                                        "Cannot find required field IMPL_MAJOR_VERSION_INTERNAL in CodeGeneratorVersions class"))
        val implMajorVersion = (implMajorVersionField.initializer as? PsiLiteralExpression)?.value as? Int
                               ?: return@readAction Result.failure(
                                 RuntimeException("Cannot detect code generator Impl Major version by field"))

        val implMinorVersionField = generatorVersionsClass.findFieldByName("IMPL_MINOR_VERSION_INTERNAL", false)
                                    ?: return@readAction Result.failure(
                                      RuntimeException(
                                        "Cannot find required field IMPL_MINOR_VERSION_INTERNAL in CodeGeneratorVersions class"))
        val implMinorVersion = (implMinorVersionField.initializer as? PsiLiteralExpression)?.value as? Int
                               ?: return@readAction Result.failure(
                                 RuntimeException("Cannot detect code generator Impl Minor version by field"))

        val artifactVersion = "$apiVersion.$implMajorVersion.$implMinorVersion"
        thisLogger().debug("Calculated target API version: $artifactVersion")
        return@readAction Result.success(artifactVersion)
      }
    }
  }

  companion object {
    fun getInstance(project: Project): CodegenJarLoader = project.service()

    private const val GROUP_ID = "com.jetbrains.intellij.platform"
    private const val ARTIFACT_ID = "workspace-model-codegen-impl"
    private val INTELLIJ_DEPENDENCIES_DESCRIPTION = RemoteRepositoryDescription(
      "intellij-dependencies",
      "Intellij Dependencies",
      "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies",
    )
  }
}