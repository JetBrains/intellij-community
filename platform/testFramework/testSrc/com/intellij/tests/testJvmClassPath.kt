// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions")
package com.intellij.tests

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * Returns the class path of the current test JVM.
 * A test passes it to a child JVM that runs a `main` function of [testClass].
 */
internal fun testJvmClassPath(testClass: Class<*>): String = buildList {
  addAll(System.getProperty("java.class.path").split(File.pathSeparator))
  for (loader in generateSequence(testClass.classLoader) { it.parent }) {
    when (loader) {
      is URLClassLoader -> addAll(loader.urLs.map { Path.of(it.toURI()).toString() })
      else -> {
        val getFiles = loader.javaClass.methods.singleOrNull { it.name == "getFiles" && it.parameterCount == 0 }
        val files = getFiles?.invoke(loader) as? List<*>
        if (files != null) addAll(files.map { it.toString() })
      }
    }
  }
}.distinct().joinToString(File.pathSeparator)
