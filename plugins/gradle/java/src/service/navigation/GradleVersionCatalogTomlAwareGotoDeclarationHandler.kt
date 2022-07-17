// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.tail
import org.jetbrains.plugins.gradle.service.project.getVersionCatalogsData
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionProperty
import org.jetbrains.plugins.gradle.service.resolve.getRootGradleProjectPath
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlRecursiveVisitor
import org.toml.lang.psi.TomlTable
import java.nio.file.Path

class GradleVersionCatalogTomlAwareGotoDeclarationHandler : GotoDeclarationHandler {

  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
    if (sourceElement == null) {
      return null
    }
    val resolved = sourceElement.parentOfType<GrReferenceExpression>()?.resolve()
    if (resolved is GradleExtensionProperty && resolved.name == "libs") {
      val toml = findTomlFile(sourceElement, resolved.name)
      if (toml != null) {
        return arrayOf(toml)
      }
    }
    if (resolved is PsiMethod) {
      return resolved.resolveInToml(sourceElement)?.let { arrayOf(it) }
    }
    return null
  }
}

private fun findTomlFile(context: PsiElement, name: String): TomlFile? {
  val path = context.getRootGradleProjectPath() ?: return null
  val settingsFile = Path.of(path, GradleConstants.SETTINGS_FILE_NAME)
  val settingsPsi = VfsUtil.findFile(settingsFile, false)?.let { PsiUtilCore.getPsiFile(context.project, it) }.castSafelyTo<GroovyFileBase>() ?: return null
  val tomlRegistry = settingsPsi.getVersionCatalogsData()
  val toml = tomlRegistry[name] ?: return null
  return VfsUtil.findFile(toml, false)?.let { PsiManager.getInstance(context.project).findFile(it) }?.castSafelyTo<TomlFile>()
}

private fun PsiMethod.resolveInToml(sourceElement: PsiElement): PsiElement? {
  val containingClasses = mutableListOf(containingClass ?: return null)
  while (containingClasses.last().containingClass != null) {
    containingClasses.add(containingClasses.last().containingClass!!)
  }
  containingClasses.reverse()
  val name = containingClasses.first().name?.substringAfter(LIBRARIES_FOR_PREFIX) ?: return null
  val toml = listOf(GroovyPropertyUtils.decapitalize(name), name).firstNotNullOfOrNull { findTomlFile(sourceElement, it) }
             ?: return null
  val tomlVisitor = TomlVersionCatalogVisitor(containingClasses.tail(), this)
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
      if (targetMethod.returnType?.resolve()?.qualifiedName != GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER) {
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
        tomlEntry.key.segments.firstOrNull()?.name?.split("_", "-")?.joinToString("", transform = GroovyPropertyUtils::capitalize)
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




