// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml

import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.util.asSafely
import com.intellij.util.containers.tail
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER_CONVERTIBLE
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogsLocator
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles
import org.jetbrains.plugins.gradle.util.*
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.name

private fun getTableEntries(context: PsiElement, tableName: @NlsSafe String) : List<TomlKeySegment> {
  val file = context.containingFile.asSafely<TomlFile>() ?: return emptyList()
  val targetTable = file.childrenOfType<TomlTable>().find { it.header.key?.name == tableName } ?: return emptyList()
  return targetTable.childrenOfType<TomlKeyValue>().mapNotNull { it.key.segments.singleOrNull() }
}

internal fun getVersions(context: PsiElement): List<TomlKeySegment> = getTableEntries(context, "versions")

internal fun getLibraries(context: PsiElement): List<TomlKeySegment> = getTableEntries(context, "libraries")

fun String.getVersionCatalogParts() : List<String> = split("_", "-")

fun findTomlFile(context: PsiElement, name: String) : TomlFile? {
  val file = getVersionCatalogFiles(context.project)[name] ?: return null
  return PsiManager.getInstance(context.project).findFile(file)?.asSafely<TomlFile>()
}

private fun findTomlFileDynamically(context: PsiElement, name: String): VirtualFile? {
  val module = context.containingFile?.originalFile?.virtualFile?.let {
    ProjectFileIndex.getInstance(context.project).getModuleForFile(it)
  } ?: return null
  val tomlPath = context.project.service<VersionCatalogsLocator>().getVersionCatalogsForModule(module)[name] ?: return null
  return VfsUtil.findFile(tomlPath, false)
}

fun findCatalogKey(tomlFile: TomlFile, declarationPath: String): PsiElement? {
  val prefix = listOf("versions.", "bundles.", "plugins.")
  val section: String
  val target: String
  if (prefix.none { declarationPath.startsWith(it) }) {
    section = "libraries"
    target = declarationPath
  }
  else {
    section = declarationPath.substringBefore('.')
    target = declarationPath.substringAfter('.')
  }

  // At the root level, look for the right section (versions, libraries, etc)
  tomlFile.children.forEach { element ->
    // [table]
    // alias =
    if (element is TomlHeaderOwner) {
      val keyText = element.header.key?.text
      if (keysMatch(keyText, section)) {
        if (element is TomlKeyValueOwner) {
          return findAlias(element,target)
        }
      }
    }
    // for corner cases
    if (element is TomlKeyValue) {
      val keyText = element.key.text
      // libraries.alias = ""
      if (keysMatch(keyText, "$section.$target")) {
        return element
      } else
        // libraries = { alias = ""
        if(element.value is TomlInlineTable && keysMatch(keyText, section)) {
          return findAlias(element.value as TomlInlineTable,target)
        }
    }
  }
  return null
}

private fun findAlias(valueOwner: TomlKeyValueOwner, target:String):PsiElement?{
  for (entry in valueOwner.entries) {
    val entryKeyText = entry.key.text
    if (keysMatch(entryKeyText, target)) {
      return entry
    }
  }
  return null
}

private fun keysMatch(s1: String?, s2: String): Boolean {
  s1 ?: return false
  if (s1.length != s2.length) {
    return false
  }
  for (i in s1.indices) {
    if (s1[i].normalize() != s2[i].normalize()) {
      return false
    }
  }
  return true
}

private fun Char.normalize(): Char {
  if (this == '-' || this == '_') {
    return '.'
  }
  return this
}


/**
 * @param method a method within a synthetic version catalog accessor class. The method must not return an accessor (i.e. it should be a leaf method).
 * @param context a context element within any gradle buildscript.
 * @return an element within TOML file, that describes [method]
 */
fun findOriginInTomlFile(method: PsiMethod, context: PsiElement): PsiElement? {
  val containingClasses = mutableListOf(method.containingClass ?: return null)
  while (containingClasses.last().containingClass != null) {
    containingClasses.add(containingClasses.last().containingClass!!)
  }
  containingClasses.reverse()
  val name = containingClasses.first().name?.substringAfter(LIBRARIES_FOR_PREFIX) ?: return null
  val toml = listOf(StringUtil.decapitalize(name), name).firstNotNullOfOrNull { findTomlFile(context, it) }
             ?: return null
  val tomlVisitor = TomlVersionCatalogVisitor(containingClasses.tail(), method)
  toml.accept(tomlVisitor)
  return tomlVisitor.resolveTarget
}

private class TomlVersionCatalogVisitor(containingClasses: List<PsiClass>, val targetMethod: PsiMethod) : TomlRecursiveVisitor() {
  private val containingClasses: MutableList<PsiClass> = ArrayList(containingClasses)
  var resolveTarget: PsiElement? = null

  override fun visitTable(element: TomlTable) {
    val headerKind = element.header.key?.segments?.singleOrNull()?.name?.getTomlHeaderKind() ?: return
    val firstClass = containingClasses.firstOrNull()
    if (firstClass != null) {
      val firstClassKind = firstClass.getTomlHeaderKind() ?: return
      if (headerKind != firstClassKind) {
        return
      }
      val returnType = targetMethod.returnType?.asSafely<PsiClassType>()?.resolve()
      if (!(InheritanceUtil.isInheritor(returnType, GRADLE_API_PROVIDER_PROVIDER) ||
            InheritanceUtil.isInheritor(returnType, GRADLE_API_PROVIDER_PROVIDER_CONVERTIBLE))) {
        return
      }
      return resolveAsComponent(element.entries)
    }
    else {
      when (targetMethod.name) {
        METHOD_GET_PLUGINS -> if (headerKind == TomlHeaderKind.PLUGINS) resolveTarget = element else return
        METHOD_GET_BUNDLES -> if (headerKind == TomlHeaderKind.BUNDLES) resolveTarget = element else return
        METHOD_GET_VERSIONS -> if (headerKind == TomlHeaderKind.VERSIONS) resolveTarget = element else return
        else -> if (headerKind == TomlHeaderKind.LIBRARIES) resolveAsComponent(element.entries) else return
      }
    }
  }

  private fun resolveAsComponent(values: List<TomlKeyValue>) {
    val camelCasedName = getCapitalizedAccessorName(targetMethod)
    for (tomlEntry in values) {
      val keyName =
        tomlEntry.key.segments.firstOrNull()?.name?.getVersionCatalogParts()?.joinToString("", transform = StringUtil::capitalize)
        ?: continue
      if (camelCasedName == keyName) {
        resolveTarget = tomlEntry
        return
      }
    }
  }
}

private enum class TomlHeaderKind {
  VERSIONS,
  BUNDLES,
  LIBRARIES,
  PLUGINS
}

private fun PsiClass.getTomlHeaderKind(): TomlHeaderKind? {
  val name = name ?: return null
  return when {
    name.endsWith(VERSION_ACCESSORS_SUFFIX) -> TomlHeaderKind.VERSIONS
    name.endsWith(BUNDLE_ACCESSORS_SUFFIX) -> TomlHeaderKind.BUNDLES
    name.endsWith(PLUGIN_ACCESSORS_SUFFIX) -> TomlHeaderKind.PLUGINS
    name.endsWith(LIBRARY_ACCESSORS_SUFFIX) -> TomlHeaderKind.LIBRARIES
    else -> null
  }
}

private fun String.getTomlHeaderKind(): TomlHeaderKind? =
  when (this) {
    TOML_TABLE_VERSIONS -> TomlHeaderKind.VERSIONS
    TOML_TABLE_LIBRARIES -> TomlHeaderKind.LIBRARIES
    TOML_TABLE_BUNDLES -> TomlHeaderKind.BUNDLES
    TOML_TABLE_PLUGINS -> TomlHeaderKind.PLUGINS
    else -> null
  }

private const val TOML_TABLE_VERSIONS = "versions"
private const val TOML_TABLE_LIBRARIES = "libraries"
private const val TOML_TABLE_BUNDLES = "bundles"
private const val TOML_TABLE_PLUGINS = "plugins"

private const val METHOD_GET_PLUGINS = "getPlugins"
private const val METHOD_GET_VERSIONS = "getVersions"
private const val METHOD_GET_BUNDLES = "getBundles"