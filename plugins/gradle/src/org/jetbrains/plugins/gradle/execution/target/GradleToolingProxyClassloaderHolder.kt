// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction
import org.gradle.tooling.BuildAction
import org.jetbrains.plugins.gradle.service.execution.GRADLE_TOOLING_EXTENSION_PROXY_CLASSES

private val DEFAULT_TOOLING_EXTENSION_CLASSLOADERS = GRADLE_TOOLING_EXTENSION_PROXY_CLASSES.map { it.classLoader }.toSet()

internal class GradleToolingProxyClassloaderHolder {

  private val buildActionClassloaders = LinkedHashSet<ClassLoader>()

  fun add(buildAction: BuildAction<*>) {
    if (buildAction is GradleModelFetchAction) {
      for (klass in buildAction.modelProvidersClasses) {
        buildActionClassloaders.add(klass.classLoader)
      }
    }
  }

  fun getClassloader(): ClassLoader {
    return object : ClassLoader() {
      override fun findClass(name: String): Class<*> {
        for (delegate in DEFAULT_TOOLING_EXTENSION_CLASSLOADERS + buildActionClassloaders) {
          try {
            return Class.forName(name, false, delegate)
          }
          catch (_: ClassNotFoundException) {

          }
        }
        throw ClassNotFoundException(name)
      }
    }
  }
}
