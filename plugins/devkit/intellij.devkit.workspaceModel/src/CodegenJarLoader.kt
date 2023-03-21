// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import org.jetbrains.concurrency.await
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import java.io.File
import java.net.URLClassLoader
import java.util.*


@Service(Service.Level.PROJECT)
class CodegenJarLoader(val project: Project) {
  private val classLoaderToVersion = mutableMapOf<Int, ClassLoader>()

  suspend fun getClassLoader(): ClassLoader? {
    val targetApiVersion = calculateTargetApiVersion() ?: return null
    val result = classLoaderToVersion[targetApiVersion]
    return if (result == null) {
      val classLoader = loadJar() ?: return null
      classLoaderToVersion[targetApiVersion] = classLoader
      classLoader
    } else {
      result
    }
  }

  private suspend fun loadJar(): ClassLoader? {
    val calculateTargetApiVersion = calculateTargetApiVersion()
    thisLogger().debug("Calculated target API version: $calculateTargetApiVersion")
    val codegenLibraryDescription = JpsMavenRepositoryLibraryDescriptor(GROUP_ID, ARTIFACT_ID,
                                                                        VERSION, false, emptyList())

    val promise = JarRepositoryManager.loadDependenciesAsync(project, codegenLibraryDescription, setOf(ArtifactKind.ARTIFACT),
                                                             listOf(INTELLIJ_DEPENDENCIES_DESCRIPTION), null)
    val roots = promise.await()
    val orderRoot = roots.firstOrNull() ?: return null

    val pathToJar = File(orderRoot.file.path.removeSuffix("!/")).toURI().toURL()
    thisLogger().info("Path to jar: $pathToJar")
    return URLClassLoader(arrayOf(pathToJar), this.javaClass.classLoader)
  }

  private fun calculateTargetApiVersion(): Int? {
    val allScope = GlobalSearchScope.allScope(project)
    val generatorVersionsClass = JavaPsiFacade.getInstance(project).findClass(CodeGeneratorVersions::class.java.name, allScope) ?: return null
    thisLogger().debug("Reading generator version from: ${generatorVersionsClass.containingFile.containingDirectory}")
    val versionField = generatorVersionsClass.findFieldByName("API_VERSION_INTERNAL", false) ?: return null
    return (versionField.initializer as? PsiLiteralExpression)?.value as? Int
  }

  companion object {
    fun getInstance(project: Project): CodegenJarLoader = project.service()

    private const val GROUP_ID = "com.jetbrains.intellij.platform"
    private const val ARTIFACT_ID = "workspace-model-codegen-impl"
    private const val VERSION = "0.0.6"
    private val INTELLIJ_DEPENDENCIES_DESCRIPTION = RemoteRepositoryDescription(
      "intellij-dependencies",
      "Intellij Dependencies",
      "https://packages.jetbrains.team/maven/p/ide-accessibility-assistant/codegen-test",
    )
  }
}