// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage.broker

import com.intellij.grazie.GrazieDynamic
import org.languagetool.broker.ResourceDataBroker
import java.io.InputStream
import java.net.URL
import java.util.*

internal object GrazieDynamicDataBroker : ResourceDataBroker {
  override fun getAsURL(path: String) = GrazieDynamic.getResource(path)

  override fun getAsStream(path: String) = GrazieDynamic.getResourceAsStream(path)

  override fun getResourceBundle(baseName: String, locale: Locale) = GrazieDynamic.getResourceBundle(baseName, locale)

  override fun getFromResourceDirAsStream(path: String): InputStream {
    val completePath = getCompleteResourceUrl(path)
    return getAsStream(completePath) ?: throw IllegalArgumentException("Path $path not found in class path at $completePath")
  }

  override fun getFromResourceDirAsLines(path: String): List<String> {
    val lines: MutableList<String> = ArrayList()
    getFromResourceDirAsStream(path).use { stream ->
      stream.bufferedReader().useLines { lines.addAll(it) }
    }
    return lines
  }

  override fun getFromResourceDirAsUrl(path: String): URL {
    val completePath = getCompleteResourceUrl(path)
    val resource = getAsURL(completePath)
    require(resource != null) { "Path $path not found in class path at $completePath" }
    return resource
  }

  private fun getCompleteResourceUrl(path: String) = appendPath(resourceDir, path)

  override fun getFromRulesDirAsStream(path: String): InputStream {
    val completePath = getCompleteRulesUrl(path)
    val resourceAsStream = getAsStream(completePath)
    require(resourceAsStream != null) { "Path $path not found in class path at $completePath" }
    return resourceAsStream
  }

  override fun getRulesDir(): String {
    return ResourceDataBroker.RULES_DIR;
  }

  override fun getFromResourceDirAsUrls(path: String): MutableList<URL> {
    val completePath = getCompleteResourceUrl(path)
    val resources = GrazieDynamic.getResources(completePath).toMutableList()
    require(resources.isNotEmpty()) { "Path $path not found in class path at $completePath" }
    return resources
  }

  override fun getAsURLs(path: String): MutableList<URL> {
    val resources = GrazieDynamic.getResources(path).toMutableList()
    require(resources.isNotEmpty()) { "Path $path not found in class path at $path" }
    return resources
  }

  override fun getFromRulesDirAsUrl(path: String): URL {
    val completePath = getCompleteRulesUrl(path)
    val resource = getAsURL(completePath)
    require(resource != null) { "Path $path not found in class path at $completePath" }
    return resource
  }

  private fun getCompleteRulesUrl(path: String): String = appendPath(rulesDir, path)

  private fun appendPath(baseDir: String, path: String): String {
    val completePath = StringBuilder(baseDir)
    if (!this.rulesDir.endsWith("/") && !path.startsWith("/")) {
      completePath.append('/')
    }
    if (this.rulesDir.endsWith("/") && path.startsWith("/") && path.length > 1) {
      completePath.append(path.substring(1))
    }
    else {
      completePath.append(path)
    }
    return completePath.toString()
  }

  override fun resourceExists(path: String) = getAsURL(getCompleteResourceUrl(path)) != null

  override fun ruleFileExists(path: String) = getAsURL(getCompleteRulesUrl(path)) != null

  override fun getResourceDir(): String {
    return ResourceDataBroker.RESOURCE_DIR;
  }
}
