// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
import org.gradle.tooling.BuildAction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.GRADLE_TOOLING_EXTENSION_PROXY_CLASSES

private val DEFAULT_TOOLING_EXTENSION_CLASSLOADERS = GRADLE_TOOLING_EXTENSION_PROXY_CLASSES.map { it.classLoader }.toSet()

@ApiStatus.Internal
internal class GradleToolingProxyClassloaderHolder {

  private val buildActionClassloaders = LinkedHashSet<ClassLoader>()

  fun add(buildAction: BuildAction<*>) {
    if (buildAction is GradleModelFetchAction) {
      for (klass in buildAction.modelProvidersClasses) {
        buildActionClassloaders.add(klass.classLoader)
      }
    }
  }

  fun getClassloaders(): Collection<ClassLoader> {
    return DEFAULT_TOOLING_EXTENSION_CLASSLOADERS + buildActionClassloaders
  }
}
