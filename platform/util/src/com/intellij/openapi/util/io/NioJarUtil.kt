// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarInputStream

fun getJarAttribute(path: Path, attribute: Attributes.Name): String? {
  try {
    return JarInputStream(Files.newInputStream(path)).use { jarInputStream ->
        jarInputStream.manifest?.mainAttributes?.getValue(attribute)
    }
  }
  catch (_: IOException) {
    return null
  }
}