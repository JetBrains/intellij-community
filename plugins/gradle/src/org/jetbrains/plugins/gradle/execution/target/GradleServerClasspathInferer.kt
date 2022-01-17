// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.openapi.application.PathManager
import com.intellij.util.io.URLUtil.urlToFile
import org.gradle.tooling.BuildAction
import org.gradle.tooling.internal.provider.serialization.ClasspathInferer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ProjectImportAction
import java.net.URL

@ApiStatus.Internal
internal class GradleServerClasspathInferer {
  private val classesUsedInBuildAction = LinkedHashSet<Class<*>>()
  private val classesUsedByGradleProxyApp = LinkedHashSet<Class<*>>()
  fun add(buildAction: BuildAction<*>) {
    if (buildAction is ProjectImportAction) {
      classesUsedInBuildAction.addAll(buildAction.modelProvidersClasses)
    }
  }

  fun add(clazz: Class<*>) {
    classesUsedByGradleProxyApp.add(clazz)
  }

  fun getClasspath(): List<String> {
    val paths = LinkedHashSet<String>()
    classesUsedByGradleProxyApp.mapNotNullTo(paths) { PathManager.getJarPathForClass(it) }
    val classpathInferer = ClasspathInferer()
    for (clazz in classesUsedInBuildAction) {
      val classpathUrls = LinkedHashSet<URL>()
      classpathInferer.getClassPathFor(clazz, classpathUrls)
      for (url in classpathUrls) {
        paths.add(urlToFile(url).path)
      }
    }
    return paths.toList()
  }

  fun getClassloaders(): Collection<ClassLoader> {
    return (classesUsedInBuildAction + classesUsedByGradleProxyApp)
      .map { it.classLoader }
      .toSet()
  }
}