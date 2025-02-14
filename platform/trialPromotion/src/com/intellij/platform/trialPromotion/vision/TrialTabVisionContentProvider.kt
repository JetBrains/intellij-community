// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.trialPromotion.vision

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Path

import com.intellij.platform.trialPromotion.TrialPageKind

// should be extended in product modules
open class TrialTabVisionContentProvider {

  companion object {
    suspend fun getInstance(): TrialTabVisionContentProvider = serviceAsync()
  }

  suspend fun isAvailable(): Boolean = contents.all { (_, src) -> src.checkAvailability() }

  protected open fun getResource(kind: TrialPageKind): ContentSource =
    throw RuntimeException("Override this service in your product")

  private val contents: Map<TrialPageKind, ContentSource> by lazy {
    val testModePaths = System.getProperty(PROPERTY_TRIAL_VISION_PATHS)
      ?.let { json.decodeFromString<TestModeResourcePaths>(it) }

    if (testModePaths != null) {
      val paths = testModePaths.paths
      logger.info("Trial tab test mode engaged: loading resources from $paths")
      paths.mapValues { (_, path) -> PathContentSource(path) }
    } else {
      TrialPageKind.entries.associateWith(::getResource)
    }
  }

  @Serializable
  private class TestModeResourcePaths(val paths: Map<TrialPageKind, Path>)

  private val json = Json { ignoreUnknownKeys = true }

  @OptIn(ExperimentalSerializationApi::class)
  private suspend fun loadVisionFile(content: ContentSource): Container = content.openStream()?.use { inputStream ->
    json.decodeFromStream<Container>(inputStream)
  } ?: error("Vision page not found")

  internal suspend fun getContentMap(): Map<TrialPageKind, Container> = contents.mapValues { loadVisionFile(it.value) }
}

private const val PROPERTY_TRIAL_VISION_PATHS = "intellij.trial.vision.paths"

private val logger = logger<TrialTabVisionContentProvider>()
