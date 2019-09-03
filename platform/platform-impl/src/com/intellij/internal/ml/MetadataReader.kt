// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

class MetadataReader(private val featuresDirectory: String) {

  fun binaryFeatures(): String = resourceContent("binary.json")
  fun floatFeatures(): String = resourceContent("float.json")
  fun categoricalFeatures(): String = resourceContent("categorical.json")
  fun allKnown(): String = resourceContent("all_features.json")
  fun featureOrderDirect(): List<String> = resourceContent("features_order.txt").lines()

  fun extractVersion(): String? {
    val resource = MetadataReader::class.java.classLoader.getResource("$featuresDirectory/binary.json")
    if (resource == null) return null
    val result = resource.file.substringBeforeLast(".jar!", "").substringAfterLast("-", "")
    return if (result.isBlank()) null else result
  }

  private fun resourceContent(fileName: String): String {
    val resource = "$featuresDirectory/$fileName"
    val fileStream = MetadataReader::class.java.classLoader.getResourceAsStream(resource)
    if (fileStream == null) throw InconsistentMetadataException("Metadata file not found: $resource")
    return fileStream.bufferedReader().readText()
  }
}
