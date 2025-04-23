// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.platform.whatsNew

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

open class WhatsNewInVisionContentProvider {

  companion object {
    suspend fun getInstance(): WhatsNewInVisionContentProvider = serviceAsync()
    private const val visionFileName = "vision.json"
  }

  suspend fun isAvailable(): Boolean {
    return content.checkAvailability()
  }

  @Serializable
  internal class Container(val entities: List<Page>)

  @Serializable
  internal data class Page(val id: Int,
                           val actions: List<Action>,
                           val html: String,
                           val languages: List<Language>,
                           val publicVars: List<PublicVar>)

  @Serializable
  internal data class Action(val value: String, val description: String)

  @Serializable
  internal data class Language(val code: String)

  @Serializable
  internal data class PublicVar(val value: String, val description: String)

  @Deprecated("Use getResource(resourceName: String) instead")
  protected fun getResourceName(): String {
    return getResourceNameByPath(visionFileName)
  }

  protected open fun getResourceNameByPath(path: String): String {
    val appName = ApplicationNamesInfo.getInstance().productName.lowercase()
    val isEap = ApplicationInfo.getInstance().isEAP
    return "com/intellij/platform/whatsNew/$appName${
      if (isEap) {
        "/eap/$path"
      }
      else {
        "/release/$path"
      }
    }"
  }

  protected open fun getResource(resourceName: String): ContentSource =
    ResourceContentSource(WhatsNewInVisionContentProvider::class.java.classLoader, resourceName)

  @Deprecated("Use getResource(resourceName: String) instead")
  protected open fun getResource(): ContentSource =
    ResourceContentSource(WhatsNewInVisionContentProvider::class.java.classLoader, getResourceName())

  private val content: ContentSource by lazy {
    System.getProperty(PROPERTY_WHATS_NEW_VISION_JSON)?.let { testResourcePath ->
      logger.info("What's New test mode engaged: loading resource from \"$testResourcePath\".")
      PathContentSource(Path(testResourcePath))
    } ?: getResource()
  }

  private val json = Json { ignoreUnknownKeys = true }
  internal suspend fun getContent(): Container {
    return content.openStream()?.use { inputStream ->
      json.decodeFromStream<Container>(inputStream)
    } ?: error("Vision page not found")
  }

  internal fun getWhatsNewResource(resourceName: String): ContentSource = getResource(getResourceNameByPath(resourceName))
}

private const val PROPERTY_WHATS_NEW_VISION_JSON = "intellij.whats.new.vision.json"

private val logger = logger<WhatsNewInVisionContentProvider>()

interface ContentSource {
  suspend fun openStream(): InputStream?
  suspend fun checkAvailability(): Boolean
}
private class PathContentSource(private val path: Path) : ContentSource {
  override suspend fun openStream() = withContext(Dispatchers.IO) {
    path.inputStream()
  }
  override suspend fun checkAvailability() = withContext(Dispatchers.IO) {
    path.isRegularFile()
  }
}
class ResourceContentSource(private val classLoader: ClassLoader, private val resourceName: String) : ContentSource {
  override suspend fun openStream(): InputStream? = withContext(Dispatchers.IO) {
    classLoader.getResourceAsStream(resourceName)
  }
  override suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
    classLoader.getResource(resourceName) != null
  }
}