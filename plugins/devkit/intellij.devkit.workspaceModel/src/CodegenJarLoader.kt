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

  suspend fun getClassLoader(): ClassLoader? {
    val artifactVersion = calculateArtifactVersion() ?: return null
    val result = classLoaderToVersion[artifactVersion]
    return if (result == null) {
      val classLoader = loadJar() ?: return null
      classLoaderToVersion[artifactVersion] = classLoader
      classLoader
    } else {
      result
    }
  }

  private suspend fun loadJar(): ClassLoader? {
    val artifactVersion = calculateArtifactVersion() ?: return null
    val codegenLibraryDescription = JpsMavenRepositoryLibraryDescriptor(GROUP_ID, ARTIFACT_ID, artifactVersion,
                                                                        false, emptyList())
    val roots = try {
      JarRepositoryManager.loadDependenciesAsync(project, codegenLibraryDescription, setOf(ArtifactKind.ARTIFACT),
                                                 listOf(INTELLIJ_DEPENDENCIES_DESCRIPTION), null).await()
    } catch (ex: Exception) {
      thisLogger().warn("Exception at codegen version $artifactVersion loading ", ex)
      return null
    }

    val pathToJar = PathUtil.getLocalPath(roots.firstOrNull()?.file)?.toNioPath() ?: return null
    thisLogger().info("Path to jar: $pathToJar")
    return UrlClassLoader.build().files(listOf(pathToJar)).parent(this.javaClass.classLoader).get()
  }

  private fun calculateArtifactVersion(): String? {
    val allScope = GlobalSearchScope.allScope(project)
    val generatorVersionsClass = JavaPsiFacade.getInstance(project).findClass(CodeGeneratorVersions::class.java.name, allScope) ?: return null
    thisLogger().debug("Reading generator version from: ${generatorVersionsClass.containingFile.containingDirectory}")
    val versionField = generatorVersionsClass.findFieldByName("API_VERSION_INTERNAL", false) ?: return null
    val apiVersion = (versionField.initializer as? PsiLiteralExpression)?.value as? Int ?: return null
    thisLogger().debug("Calculated target API version: $apiVersion")
    return "$VERSION$apiVersion"
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