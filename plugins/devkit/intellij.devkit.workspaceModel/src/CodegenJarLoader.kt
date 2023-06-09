// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPath
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PathUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import org.jetbrains.concurrency.await
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor


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
    } else {
      result
    }
  }

  private suspend fun loadJar(): ClassLoader {
    val artifactVersion = calculateArtifactVersion().getOrThrow()
    val codegenLibraryDescription = JpsMavenRepositoryLibraryDescriptor(GROUP_ID, ARTIFACT_ID, artifactVersion,
                                                                        false, emptyList())
    val roots =
      JarRepositoryManager.loadDependenciesAsync(project, codegenLibraryDescription, setOf(ArtifactKind.ARTIFACT),
                                                 listOf(INTELLIJ_DEPENDENCIES_DESCRIPTION), null).await()

    val pathToJar = PathUtil.getLocalPath(roots.firstOrNull()?.file)?.toNioPath() ?: error("Cannot get path ${roots.firstOrNull()?.file}")
    thisLogger().info("Path to jar: $pathToJar")
    return UrlClassLoader.build().files(listOf(pathToJar)).parent(this.javaClass.classLoader).get()
  }

  private fun calculateArtifactVersion(): Result<String> {
    val allScope = GlobalSearchScope.allScope(project)
    val generatorVersionsClass = JavaPsiFacade.getInstance(project).findClass(CodeGeneratorVersions::class.java.name, allScope)
                                 ?: return Result.failure(
                                   RuntimeException(
                                     "Cannot find class CodeGeneratorVersions in source code. Probably issue with kotlin compilation caches.")
                                 )
    thisLogger().debug("Reading generator version from: ${generatorVersionsClass.containingFile.containingDirectory}")
    val versionField = generatorVersionsClass.findFieldByName("API_VERSION_INTERNAL", false)
                       ?: return Result.failure(
                         RuntimeException("Cannot find required field API_VERSION_INTERNAL in CodeGeneratorVersions class"))
    val apiVersion = (versionField.initializer as? PsiLiteralExpression)?.value as? Int ?: return Result.failure(
      RuntimeException("Cannot detect code generator version by field"))
    thisLogger().debug("Calculated target API version: $apiVersion")
    return Result.success("$VERSION$apiVersion")
  }

  companion object {
    fun getInstance(project: Project): CodegenJarLoader = project.service()

    private const val GROUP_ID = "com.jetbrains.intellij.platform"
    private const val ARTIFACT_ID = "workspace-model-codegen-impl"
    private const val VERSION = "0.0."
    private val INTELLIJ_DEPENDENCIES_DESCRIPTION = RemoteRepositoryDescription(
      "intellij-dependencies",
      "Intellij Dependencies",
      "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies",
    )
  }
}