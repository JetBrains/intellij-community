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
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

open class WhatsNewInVisionContentProvider {

  companion object {
    suspend fun getInstance(): WhatsNewInVisionContentProvider = serviceAsync()
    const val DEFAULT_VISION_JSON_FILE_NAME: String = "vision-in-product-pages.json"
  }

  suspend fun isAvailable(): Boolean {
    return content.checkAvailability()
  }

  /**
   * Files that will be probed while looking for Vision content.
   */
  open val visionJsonFileNames: List<String> = listOf(
    DEFAULT_VISION_JSON_FILE_NAME,
    "vision.json" // legacy name for compatibility
  )

  /**
   * Class loader that will be used for resource lookup.
   * By default, will be the class loader of this object (even if it's an override).
   */
  open val resourceClassLoader: ClassLoader
    get() = this::class.java.classLoader

  /**
   * Path to the resource directory in the class loader.
   * By default, it will be "com/intellij/platform/whatsNew/(eap|release)".
   */
  open val baseResourcePathInClassLoader: String by lazy {
    val appName = ApplicationNamesInfo.getInstance().productName.lowercase()
    val isEap = ApplicationInfo.getInstance().isEAP
    "com/intellij/platform/whatsNew/$appName${
      if (isEap) {
        "/eap"
      }
      else {
        "/release"
      }
    }"
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

  protected open fun getResourceNameByPath(path: String): String =
    "$baseResourcePathInClassLoader/$path"

  protected open fun getResource(resourceName: String): ContentSource =
    ResourceContentSource(resourceClassLoader, resourceName)

  protected fun getVisionJsonResourceNames(): List<String> = visionJsonFileNames.map(::getResourceNameByPath)

  @ApiStatus.OverrideOnly
  protected open fun getResource(): ContentSource =
    ResourceContentSource(resourceClassLoader, getVisionJsonResourceNames())

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
class ResourceContentSource(private val classLoader: ClassLoader, private val resourceNames: List<String>) : ContentSource {

  constructor(classLoader: ClassLoader, resourceName: String) : this(classLoader, listOf(resourceName))

  override suspend fun openStream(): InputStream? = withContext(Dispatchers.IO) {
    resourceNames.asSequence().map {
      classLoader.getResourceAsStream(it)
    }.firstOrNull()
  }
  override suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
    resourceNames.any { classLoader.getResource(it) != null }
  }
}