// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.XmlAttributeImpl
import com.intellij.psi.util.PsiTreeUtil.findChildrenOfType
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.utils.mapToSetOrEmpty


internal val KtProperty.isComposeResourceProperty: Boolean
  get() {
    val typeText = this.typeReference?.getTypeText() ?: return false
    return typeText in ResourceType.KNOWN_RESOURCE_NAMES
  }

/** new layout from compose v1.6.1 */
internal fun resolvePossibleDelegate(candidate: KtProperty): KtNamedFunction? {
  val lambdaExpression = (candidate.delegateExpression as KtCallExpression).lambdaArguments.firstOrNull()?.getLambdaExpression()
  return lambdaExpression
    ?.bodyExpression
    ?.firstStatement
    ?.getCalleeExpressionIfAny()
    ?.mainReference
    ?.resolve() as? KtNamedFunction
}

internal fun getResourceCandidate(kotlinSourceElement: KtProperty): KtProperty? = kotlinSourceElement
  .getter
  ?.bodyExpression
  ?.getCalleeExpressionIfAny()
  ?.mainReference
  ?.resolve() as? KtProperty

internal fun getLightResourceCandidate(kotlinSourceElement: KtProperty): KtCallExpression? = kotlinSourceElement
  .getter
  ?.bodyExpression as? KtCallExpression

private fun getInternalProperty(kotlinSourceElement: KtElement) = kotlinSourceElement.mainReference?.resolve() as? KtProperty

internal data class ResourceItem(val type: ResourceType, val id: String, val key: String?, val paths: List<String>) {

  /**
   * Retrieves a list of [PsiElement] objects representing resources based on the given paths.
   * The method attempts to locate the files specified by the paths, convert them to PSI files,
   * and extract the relevant PSI elements for further processing.
   *
   * @param project the current project context in which the lookup will be performed
   * @return a list of PsiElement objects corresponding to the found resources; returns an empty list if no resources are found or applicable
   */
  fun getPsiElements(sourceModule: Module): List<PsiElement> {
    val project = sourceModule.project
    val composeResourcesDirsDependencies = sourceModule.getComposeResourcesDirsDependencies()
    return paths.mapNotNull { path ->
      val targetResourceFile = composeResourcesDirsDependencies.firstNotNullOfOrNull { it.findFileByRelativePath(path) } ?: run {
        log.warn("Target Compose resource file for '${this@ResourceItem}' not found: $path")
        return@mapNotNull null
      }
      val targetResourcePsiFile = targetResourceFile.findPsiFile(project) ?: return@mapNotNull null
      if (type.isStringType) {
        val targetStringPsiElement = composeResourcesDirsDependencies
                                       .mapNotNull { it.findFileByRelativePath(path)?.findPsiFile(project) }
                                       .firstNotNullOfOrNull { findAttributeByResourceKey(it, this) }
                                     ?: run {
                                       log.warn("Target Compose resource string for '${this@ResourceItem}' not found: $path")
                                       return@mapNotNull null
                                     }
        val attributeValueToBeRenamed = targetStringPsiElement.children.lastOrNull() as? XmlAttributeValue ?: return@mapNotNull null
        attributeValueToBeRenamed
      }
      else {
        targetResourcePsiFile
      }
    }
  }

  private fun Module.getComposeResourcesDirsDependencies(): List<VirtualFile> =
    buildSet { ModuleUtilCore.getDependencies(this@getComposeResourcesDirsDependencies, this) }.mapNotNull { it.getComposeResourcesDir() }


  private fun findAttributeByResourceKey(targetResourcePsiFile: PsiFile, targetResourceItem: ResourceItem): XmlAttributeImpl? =
    findChildrenOfType(targetResourcePsiFile, XmlAttributeImpl::class.java).firstOrNull { it.value == targetResourceItem.key }


  companion object {
    fun fromResourceDeclaration(declaration: KtElement): ResourceItem? {
      val templateStrings = findChildrenOfType(declaration, KtStringTemplateExpression::class.java).map { it.text.trim('"', '\'') }
      if (templateStrings.isEmpty()) return null
      val type = ResourceType.fromString(templateStrings.first().substringBefore(':'))
      val paths = templateStrings.drop(1)
        .map { it.split('/').takeIf { split -> split.size > 2 }?.drop(2)?.joinToString("/") ?: it } // it's generated on Windows the same
        .filter { it.startsWith(type.dirName) }
        .map { if (type.dirName == "values") "${it.substringBefore('.')}.xml" else it }
      return ResourceItem(
        type = type,
        id = templateStrings.first(),
        key = if (type.isStringType) templateStrings[1] else null,
        paths = paths,
      )
    }
  }
}

/**
 * Retrieves a `ResourceItem` based on the provided `KtElement`.
 *
 * @param kotlinSourceElement A `KtElement` representing the source element
 *                            in Kotlin code to analyze.
 * @return A `ResourceItem` if the source element is determined to be a
 *         Compose resource property and can be successfully resolved
 *         into a resource item, otherwise returns null.
 */
internal fun getResourceItem(kotlinSourceElement: KtElement): ResourceItem? {
  val internalProperty = kotlinSourceElement as? KtProperty ?: getInternalProperty(kotlinSourceElement) ?: return null
  if (!internalProperty.isComposeResourceProperty) return null
  val candidate = getResourceCandidate(internalProperty) ?: getLightResourceCandidate(internalProperty)
  return candidate?.let { declaration ->
    ResourceItem.fromResourceDeclaration(declaration)
    ?: declaration.takeIf { it is KtProperty }?.let { resolvePossibleDelegate(it as KtProperty)?.let { ResourceItem.fromResourceDeclaration(it) } }
  }
}

/** [source](https://github.com/JetBrains/compose-multiplatform/blob/c31c761e09212eaa13014f4d0d2a6516511f859a/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/resources/ResourcesSpec.kt#L7) */
internal enum class ResourceType(val typeName: String, val resourceName: String, val isStringType: Boolean = false) {
  DRAWABLE("drawable", "DrawableResource"),
  STRING("string", "StringResource", isStringType = true),
  STRING_ARRAY("string-array", "StringArrayResource", isStringType = true),
  PLURAL_STRING("plurals", "PluralStringResource", isStringType = true),
  FONT("font", "FontResource");

  override fun toString(): String = typeName

  val dirName: String = if (isStringType) "values" else typeName

  val accessorName = if (typeName == "string-array") "array" else typeName

  companion object {
    fun fromString(str: String): ResourceType =
      entries.firstOrNull { it.typeName.equals(str, ignoreCase = true) } ?: error("Unknown resource type: '$str'.")

    val KNOWN_RESOURCE_NAMES: Set<String> = entries.mapToSetOrEmpty { it.resourceName }
  }
}

/** Returns sourceSet name from a module name
 *
 * - `projectName.composeApp.commonMain` -> `commonMain`
 * - `projectName.composeApp.iosMain` -> `iosMain`
 * - except for the main Android module which should be `projectName.composeApp.main` -> `androidMain`
 */
internal fun Module.getSourceSetNameFromComposeResourcesDir(): String =
  name.substringAfterLast('.').takeUnless { it == "main" } ?: "androidMain"

/** Returns the module name from an external project ID represented by this string, trimming leading ':' */
internal fun String.getModuleName(): String? = when (count { it == ':' }) {
  0 -> null
  1 -> drop(1) // e.g. `projectName.composeApp.commonMain` -> `:composeApp` -> composeApp
  2 -> substringBeforeLast(':').drop(1) // e.g. `projectName.composeApp.main` -> `:composeApp:main` -> composeApp
  else -> null
}

private val log by lazy { fileLogger() }