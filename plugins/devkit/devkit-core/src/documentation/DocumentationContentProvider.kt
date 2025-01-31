// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.representer.Representer
import java.io.File
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val REQUEST_TIMEOUT_MS = 5.seconds.inWholeMilliseconds
private val CACHE_TTL_MS = 8.hours.inWholeMilliseconds

@Service(Level.APP)
internal class DocumentationContentProvider(private val coroutineScope: CoroutineScope) {

  private val downloadInitialized = AtomicBoolean()
  private val contentCache = ConcurrentHashMap<DocumentationDataCoordinates, Pair<DocumentationContent, /*last updated*/ Long>?>()

  /**
   * Returns the content object for given coordinates.
   * The algorithm:
   * 1. If there is cached data, and it is not older than [CACHE_TTL_MS], return it.
   * 2. If the data is outdated:
   *     - Try to use the previously downloaded and cached file (see the last point).
   *     - If the cached file is missing, use the local file from resources ([coordinates.localPath]).
   *     - Download data asynchronously from [coordinates.url], so it is up to date on the next content request.
   *          The downloaded file is stored in [PathManager.getSystemDir] for future use.
   */
  fun getContent(coordinates: DocumentationDataCoordinates): DocumentationContent? {
    return contentCache.compute(coordinates) { key, oldValue ->
      val now = System.currentTimeMillis()
      val lastUpdated = oldValue?.second ?: 0
      if (now - lastUpdated < CACHE_TTL_MS) {
        return@compute oldValue
      }
      val content = loadLocallyCachedContent(coordinates.localPath)
      downloadContentAsync(coordinates)
      if (content != null) {
        return@compute content to System.currentTimeMillis()
      }
      if (oldValue != null) {
        return@compute oldValue
      }
      val localContent = loadContentFromResources(coordinates.localPath)
      if (localContent != null) {
        return@compute localContent to System.currentTimeMillis()
      }
      return@compute null
    }?.first
  }

  fun loadLocallyCachedContent(relativeCachePath: String): DocumentationContent? {
    return parseDocumentationContent {
      getCachedFile(relativeCachePath)
        .takeIf { it.exists() }
        ?.readText()
    }
  }

  private fun getCachedFile(relativeCachePath: String): File {
    return PathManager.getSystemDir().resolve("devkit/$relativeCachePath").toFile()
  }

  fun downloadContentAsync(coordinates: DocumentationDataCoordinates) {
    val request = HttpRequests.request(coordinates.url)
      .connectTimeout(REQUEST_TIMEOUT_MS.toInt())
      .readTimeout(REQUEST_TIMEOUT_MS.toInt())
    coroutineScope.async(Dispatchers.IO) {
      try {
        val yamlContent = request.readString()
        getCachedFile(coordinates.localPath).run {
          parentFile.run { if (!exists()) mkdirs() }
          writeText(yamlContent)
          contentCache.remove(coordinates) // so it is refreshed on the next content request
        }
      }
      catch (_: SocketTimeoutException) {
        // offline mode
      }
      catch (e: Exception) {
        logger<DocumentationContentProvider>().warn("Could not download documentation content from ${coordinates.url}", e)
      }
    }
  }

  private fun loadContentFromResources(localPath: String): DocumentationContent? {
    return parseDocumentationContent {
      this::class.java.getResourceAsStream(localPath)?.use {
        it.bufferedReader().use { br ->
          br.readText()
        }
      }
    }
  }

  private fun parseDocumentationContent(yamlContentProvider: () -> String?): DocumentationContent? {
    val yamlContent =
      try {
        yamlContentProvider()
      }
      catch (_: Exception) {
        null
      } ?: return null
    val loaderOptions = LoaderOptions().apply {
      isEnumCaseSensitive = false
    }
    val representer = Representer(DumperOptions()).apply {
      propertyUtils.isSkipMissingProperties = true
    }
    val constructor = DescriptorDocumentationConstructor(loaderOptions)
    // DumperOptions pointed in the deprecation message doesn't support skipping missing properties
    return Yaml(constructor, representer).load<DocumentationContent>(yamlContent)
      ?.takeIf { it.elements.isNotEmpty() == true }
  }

  fun initializeContentDownload() {
    if (downloadInitialized.compareAndSet(false, true)) {
      PsiDocumentationTargetProvider.EP_NAME.extensionList
        .filterIsInstance<AbstractXmlDescriptorDocumentationTargetProvider>()
        .forEach { downloadContentAsync(it.docYamlCoordinates) }
    }
  }

  companion object {
    fun getInstance(): DocumentationContentProvider = service()
  }

}

/**
 * Post-processes the parsed content objects.
 */
private class DescriptorDocumentationConstructor(loaderOptions: LoaderOptions) :
  Constructor(DocumentationContent::class.java, loaderOptions) {

  override fun constructObject(node: Node): Any {
    val obj = super.constructObject(node)
    if (obj is DocumentationContent) {
      fillMissingData(obj)
    }
    return obj
  }

  private fun fillMissingData(content: DocumentationContent) {
    for (elementWrapper in content.elements) {
      val element = elementWrapper.element ?: continue
      copyReusedObjectsExceptContainingItself(elementWrapper, null, mutableListOf())
      fillElementPathsRecursively(element, emptyList())
      fillSelfContainingElementsRecursively(elementWrapper)
    }
  }

  /**
   * This is needed for being able to set separate paths for elements that can appear
   * under multiple parents.
   * For example, in plugin.xml, the `<action>` can be located as:
   * - `<idea-plugin>` / `<actions>` / `<action>`
   * - `<idea-plugin>` / `<actions>` / `<group>` / `<action>`
   *
   * We use YAML aliases to avoid data duplication, and SnakeYAML reflects this behavior in
   * parsed objects, so it shares the same instance when it was referenced via anchor in YAML.
   * So in the example case, there would be a single `<action>` element object, and we couldn't
   * set a separate path for each case. For this reason, we copy such elements.
   * We don't copy self-containing elements (for example, `<group>`) as they would be
   * copied infinitely.
   */
  private fun copyReusedObjectsExceptContainingItself(
    elementWrapper: ElementWrapper,
    parentWrapper: ElementWrapper?,
    alreadyUsedElements: MutableList<ElementWrapper>,
  ) {
    val element = elementWrapper.element ?: return
    val parent = parentWrapper?.element
    if (parentWrapper != null && parent != null && alreadyUsedElements.contains(elementWrapper) && element.containsItself == false) {
      val elementCopy = element.copy()
      val wrapperCopy = ElementWrapper(elementCopy)
      parent.children = parent.children.replace(elementWrapper, wrapperCopy)
    }
    alreadyUsedElements.add(elementWrapper)
    for (child in element.children) {
      copyReusedObjectsExceptContainingItself(child, elementWrapper, alreadyUsedElements)
    }
  }

  private fun <E> List<E>.replace(old: E, new: E): List<E> {
    return map { if (it === old) new else it }
  }


  private fun fillElementPathsRecursively(element: Element, parentPath: List<String>) {
    val elementPath = parentPath + element.name!!
    // If an element is aliased and referenced in YAML, the same instance is shared.
    // For this reason, set the path only if it is empty, so we get the shortest paths filled.
    if (element.path.isEmpty()) {
      element.path = elementPath
    }
    for (attribute in element.attributes.mapNotNull { it.attribute }) {
      if (attribute.path.isEmpty()) {
        val attributePath = elementPath + attribute.name!!
        attribute.path = attributePath
      }
    }
    for (child in element.children) {
      fillElementPathsRecursively(child.element!!, elementPath)
    }
  }

  private fun fillSelfContainingElementsRecursively(elementWrapper: ElementWrapper) {
    val element = elementWrapper.element ?: return
    for (child in element.children) {
      fillSelfContainingElementsRecursively(child)
    }
    if (element.containsItself) {
      element.children = element.children + elementWrapper
    }
  }
}

internal data class DocumentationDataCoordinates(
  /**
   * URL to the documentation YAML on a public server (for example, a public GitHub repository).
   */
  val url: String,
  /**
   * Relative path the documentation YAML in plugin resources.
   */
  val localPath: String,
)
