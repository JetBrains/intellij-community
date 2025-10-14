// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
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

internal fun String.getVersionCatalogParts() : List<String> = split("_", "-")

fun findTomlFile(context: PsiElement, name: String): TomlFile? {
  val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return null
  val file = getVersionCatalogFiles(module)[name] ?: return null
  return context.manager.findFile(file)?.asSafely<TomlFile>()
}

private fun findTomlFileDynamically(context: PsiElement, name: String): VirtualFile? {
  val module = context.containingFile?.originalFile?.virtualFile?.let {
    ProjectFileIndex.getInstance(context.project).getModuleForFile(it)
  } ?: return null
  val tomlPath = context.project.service<VersionCatalogsLocator>().getVersionCatalogsForModule(module)[name] ?: return null
  return VfsUtil.findFile(tomlPath, false)
}

/**
 * Given a [TomlFile] and a path, returns the corresponding key element.
 * For example, given "versions.foo", it will locate the `foo =` key/value
 * pair under the `\[versions]` table and return it. As a special case,
 * `libraries` don't have to be explicitly named in the path.
 */
fun findTomlCatalogKey(tomlFile: TomlFile, declarationPath: String): PsiElement? {
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
          return findAlias(element, target)
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

private fun findAlias(valueOwner: TomlKeyValueOwner, target:String): PsiElement?{
  for (entry in valueOwner.entries) {
    val entryKeyText = entry.key.text
    if (keysMatch(entryKeyText, target)) {
      return entry
    }
  }
  return null
}

fun keysMatch(keyText: String?, reference: String): Boolean {
  keyText ?: return false
  if (keyText.length != reference.length) {
    return false
  }
  for (i in keyText.indices) {
    if(isAfterDelimiter(i, keyText)){
      // first character may be capital after `-_.` symbols in TOML
      // it still makes it equal to low case reference - Gradle implementation detail
      if(keyText[i].normalizeIgnoreCase() != reference[i].normalize())
        return false
    } else if (keyText[i].normalize() != reference[i].normalize()) {
      return false
    }
  }
  return true
}

private fun isAfterDelimiter(index: Int, s:String):Boolean =
  index > 0 && s[index-1].normalize() == '.'

private fun Char.normalizeIgnoreCase(): Char {
  if (this == '-' || this == '_') {
    return '.'
  }
  return this.lowercaseChar()
}

// Gradle converts dashed-keys or dashed_keys into dashed.keys
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
  val name = getVersionCatalogName(containingClasses.first()) ?: return null
  val toml = listOf(StringUtil.decapitalize(name), name).firstNotNullOfOrNull { findTomlFile(context, it) }
             ?: return null
  val tomlVisitor = TomlVersionCatalogVisitor(containingClasses.tail(), method)
  toml.accept(tomlVisitor)
  return tomlVisitor.resolveTarget
}

/**
 * Determines a section name (libraries / plugins / bundles / versions) the given key-value belongs to
 */
fun getTomlParentSectionName(tomlKeyValue: TomlKeyValue): String? {
  val parentTable = tomlKeyValue.parent?.asSafely<TomlTable>() ?: return null
  return parentTable.header.key?.name
}

private fun getVersionCatalogName(psiClass: PsiClass): String? {
  val name = psiClass.name?.substringAfter(LIBRARIES_FOR_PREFIX) ?: return null
  if (name.endsWith("InPluginsBlock"))
    return name.substringBefore("InPluginsBlock")
  else
    return name
}

/**
 * Tries to resolve a dependency from a synthetic accessor method.
 * @return a string in the format of `<group>:<name>:<version>`,
 * `<group>:<name>` if the version cannot be resolved or null if the dependency cannot be resolved
 */
fun getResolvedDependency(method: PsiMethod, context: PsiElement): String? {
  if (!isInVersionCatalogAccessor(method)) return null
  val origin = findOriginInTomlFile(method, context) as? TomlKeyValue ?: return null
  return when (val originValue = origin.value) {
    is TomlLiteral -> originValue.text.cleanRawString()
    is TomlInlineTable -> {
      val module = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "module" }?.value
      val moduleText = if (module != null) {
        if (module !is TomlLiteral) return null
        module.text.cleanRawString()
      }
      else {
        val group = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "group" }?.value
        val name = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "name" }?.value
        if (group == null || name == null) return null
        if (group !is TomlLiteral || name !is TomlLiteral) return null
        "${group.text.cleanRawString()}:${name.text.cleanRawString()}"
      }

      val versionEntry = originValue.entries.find { it.key.segments.firstOrNull()?.name == "version" } ?: return moduleText
      val versionText = getResolvedVersion(versionEntry) ?: return moduleText
      return "$moduleText:$versionText"
    }

    else -> return null
  }
}

/**
 * Tries to resolve a plugin from a synthetic accessor method.
 * @return a string in the format of `<id>:<version>`,
 * `<id>` if the version cannot be resolved or null if the plugin cannot be resolved
 */
fun getResolvedPlugin(method: PsiMethod, context: PsiElement): String? {
  if (!isInVersionCatalogAccessor(method)) return null
  val origin = findOriginInTomlFile(method, context) as? TomlKeyValue ?: return null
  return when (val originValue = origin.value) {
    is TomlLiteral -> originValue.text.cleanRawString()
    is TomlInlineTable -> {
      val id = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "id" }?.value
      if (id == null) return null
      if (id !is TomlLiteral) return null
      val idText = id.text.cleanRawString()

      val versionEntry = originValue.entries.find { it.key.segments.firstOrNull()?.name == "version" } ?: return idText
      val versionText = getResolvedVersion(versionEntry) ?: return idText
      "$idText:$versionText"
    }
    else -> null
  }
}

private fun getResolvedVersion(entry: TomlKeyValue): String? {
  if (entry.key.segments.firstOrNull()?.name != "version") return null

  val finalVersionKeyValue = when (entry.key.segments.size) {
    1 -> entry

    2 if entry.key.segments[1].name == "ref" -> {
      val file = entry.containingFile.asSafely<TomlFile>() ?: return null
      val versionTable = file.childrenOfType<TomlTable>().find { it.header.key?.name == TOML_TABLE_VERSIONS } ?: return null
      val entries = versionTable.childrenOfType<TomlKeyValue>()
      val key = entry.value?.asSafely<TomlLiteral>()?.text?.cleanRawString() ?: return null

      entries.find { it.key.text == key }
    }

    else -> null
  }

  return finalVersionKeyValue?.value?.asSafely<TomlLiteral>()?.text?.cleanRawString()
}

// cleans single or multiline toml string literal
private fun String.cleanRawString(): String =
  this.trim('"', '\'')
    .replace("\r", "")
    .replace("\n", "")


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