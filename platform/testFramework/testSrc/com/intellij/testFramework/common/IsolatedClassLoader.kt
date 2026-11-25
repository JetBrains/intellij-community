// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.util.lang.UrlClassLoader
import java.io.File
import java.nio.file.Path

private val hostClassLoader: ClassLoader = IsolatedClassLoader::class.java.classLoader

private fun hostClassPath(): List<Path> =
  (hostClassLoader as? UrlClassLoader)?.files
  ?: System.getProperty("java.class.path").split(File.pathSeparator).map { Path.of(it) }

/**
 * Loads its own copy of each class of the host class path, but not the classes of `com.intellij.util.lang`.
 * The host class loader gives those, so that a copy of a class sees this loader as a [UrlClassLoader].
 * A new loader starts with no loaded class and no static state, as a new JVM does.
 */
internal class IsolatedClassLoader : UrlClassLoader(build().files(hostClassPath()).parent(getPlatformClassLoader()), true) {
  override fun loadClass(name: String, resolve: Boolean): Class<*> =
    if (name.startsWith("com.intellij.util.lang.")) hostClassLoader.loadClass(name) else super.loadClass(name, resolve)

  /**
   * Calls the static method [methodName] without parameters of the copy of [className] in this loader.
   */
  fun callStatic(className: String, methodName: String) {
    loadClass(className).getMethod(methodName).invoke(null)
  }
}
