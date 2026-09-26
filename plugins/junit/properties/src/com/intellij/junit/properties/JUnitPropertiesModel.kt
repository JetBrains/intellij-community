// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.properties

import com.intellij.execution.junit.JUnitUtil
import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiExpressionEvaluator
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope.moduleRuntimeScope
import com.intellij.psi.search.ProjectScope.getLibrariesScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

internal const val JUNIT_PLATFORM_PROPERTIES_CONFIG: String = "junit-platform.properties"
internal const val JUNIT_CONSTANTS_CLASS: String = "org.junit.jupiter.engine.Constants"
internal const val JUNIT_PROPERTY_NAME_SUFFIX: String = "_PROPERTY_NAME"
internal const val JUNIT_DEFAULT_PARALLEL_EXECUTION_MODE_FIELD: String = "DEFAULT_PARALLEL_EXECUTION_MODE"

private const val JUNIT_METADATA_FILE_NAME: String = "junit-platform-configuration-metadata.json"
private const val META_INF_DIRECTORY: String = "META-INF"

internal data class JUnitPlatformProperty(
  val key: String,
  val declaration: PsiAnchor? = null,
  @NlsSafe val html: String? = null,
  val variants: List<String> = emptyList(),
)

internal fun getJUnitPlatformProperties(file: PsiFile): Map<String, JUnitPlatformProperty> {
  return CachedValuesManager.getManager(file.project).getCachedValue(file, CachedValueProvider {
    val properties = LinkedHashMap<String, JUnitPlatformProperty>()
    for (property in collectConstantsProperties(file)) {
      properties[property.key] = property
    }

    val module = ModuleUtilCore.findModuleForFile(file)
    val psiManager = file.manager
    val files = if (module != null) {
      FilenameIndex.getVirtualFilesByName(JUNIT_METADATA_FILE_NAME, JUnitUtil.getScope(module, module.project))
        .filter { it.parent?.name == META_INF_DIRECTORY }
        .mapNotNull { psiManager.findFile(it) as? JsonFile }
    }
    else {
      listOf()
    }

    for (property in parseMetadata(files)) {
      properties.merge(property.key, property) { constant, metadata ->
        constant.copy(html = metadata.html ?: constant.html, variants = metadata.variants.ifEmpty { constant.variants })
      }
    }

    return@CachedValueProvider Result.create(properties,
                                             *files.toTypedArray(),
                                             JavaLibraryModificationTracker.getInstance(file.project),
                                             PsiModificationTracker.getInstance(file.project))
  })
}

private fun collectConstantsProperties(file: PsiFile): List<JUnitPlatformProperty> {
  val module = ModuleUtilCore.findModuleForFile(file)

  val javaPsi = JavaPsiFacade.getInstance(file.project)
  val constantsClass = module?.let { javaPsi.findClass(JUNIT_CONSTANTS_CLASS, moduleRuntimeScope(it, false)) }
                       ?: javaPsi.findClass(JUNIT_CONSTANTS_CLASS, getLibrariesScope(file.project))

  val psiEvaluator = PsiExpressionEvaluator()
  return (constantsClass?.fields ?: emptyArray())
    .filter { it.name.endsWith(JUNIT_PROPERTY_NAME_SUFFIX) || it.name == JUNIT_DEFAULT_PARALLEL_EXECUTION_MODE_FIELD }
    .mapNotNull { field ->
      computePropertyName(psiEvaluator, field.initializer)
        ?.let { key -> JUnitPlatformProperty(key, PsiAnchor.create(field)) }
    }
}

internal fun computePropertyName(psiEvaluator: PsiExpressionEvaluator, nameInitializer: PsiExpression?): String? {
  if (nameInitializer == null) return null

  return psiEvaluator.computeConstantExpression(nameInitializer, true) as? String
}

private fun parseMetadata(metadataFiles: List<JsonFile>): List<JUnitPlatformProperty> {
  val result = ArrayList<JUnitPlatformProperty>()
  for (jsonFile in metadataFiles) {
    val root = jsonFile.topLevelValue as? JsonObject ?: continue
    val valueHints = root.getValueHints()
    for (property in root.getObjectArray("properties")) {
      val nameLiteral = property.getStringLiteral("name") ?: continue
      val name = nameLiteral.value
      if (name.isEmpty()) continue
      val html = property.getStringValue("description")?.let { StringUtil.escapeXmlEntities(it) }
      result.add(JUnitPlatformProperty(name, PsiAnchor.create(nameLiteral), html, valueHints[name].orEmpty()))
    }
  }
  return result
}

private fun JsonObject.getValueHints(): Map<String, List<String>> {
  val hints = LinkedHashMap<String, MutableList<String>>()
  for (hint in this.getObjectArray("hints")) {
    val name = hint.getStringValue("name") ?: continue
    val values = hint.getObjectArray("values").mapNotNull { it.getLiteralText("value") }
    if (values.isNotEmpty()) hints.getOrPut(name) { ArrayList() }.addAll(values)
  }
  return hints
}

private fun JsonObject.getObjectArray(propertyName: String): List<JsonObject> {
  return (findProperty(propertyName)?.value as? JsonArray)?.valueList?.filterIsInstance<JsonObject>() ?: emptyList()
}

private fun JsonObject.getStringLiteral(propertyName: String): JsonStringLiteral? {
  return findProperty(propertyName)?.value as? JsonStringLiteral
}

private fun JsonObject.getStringValue(propertyName: String): String? {
  return getStringLiteral(propertyName)?.value
}

private fun JsonObject.getLiteralText(propertyName: String): String? {
  return when (val value = findProperty(propertyName)?.value) {
    is JsonStringLiteral -> value.value
    is JsonLiteral -> value.text
    else -> null
  }
}