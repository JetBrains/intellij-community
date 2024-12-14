// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
import org.gradle.tooling.BuildAction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.GRADLE_TOOLING_EXTENSION_PROXY_CLASSES

@ApiStatus.Internal
internal class GradleToolingProxyClassloaderHolder(
  private val toolingProxyClassloaders: Set<ClassLoader> = getDefaultToolingProxyClassloaders(),
) {

  private val buildActionClassloaders = LinkedHashSet<ClassLoader>()

  fun add(buildAction: BuildAction<*>) {
    if (buildAction is GradleModelFetchAction) {
      for (klass in buildAction.modelProvidersClasses) {
        buildActionClassloaders.add(klass.classLoader)
      }
    }
  }

  fun getClassloaders(): Collection<ClassLoader> {
    return toolingProxyClassloaders + buildActionClassloaders
  }

  private companion object {
    fun getDefaultToolingProxyClassloaders(): Set<ClassLoader> {
      return GRADLE_TOOLING_EXTENSION_PROXY_CLASSES.map { it.classLoader }.toSet()
    }
  }
}
