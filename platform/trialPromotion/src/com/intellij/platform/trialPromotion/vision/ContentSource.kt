// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.trialPromotion.vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

interface ContentSource {
  suspend fun openStream(): InputStream?
  suspend fun checkAvailability(): Boolean
}

class PathContentSource(private val path: Path) : ContentSource {
  override suspend fun openStream(): InputStream? = withContext(Dispatchers.IO) { path.inputStream() }
  override suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) { path.isRegularFile() }
}

class ResourceContentSource(private val classLoader: ClassLoader, private val resourceName: String) : ContentSource {
  override suspend fun openStream(): InputStream? = withContext(Dispatchers.IO) { classLoader.getResourceAsStream(resourceName) }
  override suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) { classLoader.getResource(resourceName) != null }
}
