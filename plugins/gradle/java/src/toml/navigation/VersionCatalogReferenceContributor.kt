// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.navigation

import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.toml.getLibraries
import org.jetbrains.plugins.gradle.toml.getVersions
import org.toml.lang.psi.*

class VersionCatalogReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(versionRefPattern, VersionCatalogReferenceProvider(::getVersions))
    registrar.registerReferenceProvider(libraryInBundlePattern, VersionCatalogReferenceProvider(::getLibraries))
  }
}

internal val refKeyValuePattern: PsiElementPattern.Capture<TomlKeyValue> = PlatformPatterns
  .psiElement(TomlKeyValue::class.java)
  .with(RefPatternCondition())
internal val versionRefPattern: ElementPattern<TomlValue> = PlatformPatterns
  .psiElement(TomlValue::class.java)
  .withParent(refKeyValuePattern)
internal val libraryInBundlePattern: ElementPattern<TomlValue> =
  PlatformPatterns
  .psiElement(TomlValue::class.java)
  .withParents(TomlArray::class.java, TomlKeyValue::class.java, TomlTable::class.java).with(BundleTablePatternCondition())


private class RefPatternCondition : PatternCondition<TomlKeyValue>("'ref' key value") {
  override fun accepts(t: TomlKeyValue, context: ProcessingContext?): Boolean {
    val segments = t.key.segments
    if (segments.isEmpty()) {
      return false
    }
    if (segments.last().name != "ref") {
      return false
    }
    if (segments.size == 1) {
      return t.parent?.asSafely<TomlInlineTable>()?.parent?.asSafely<TomlKeyValue>()
        ?.takeIf { it.key.segments.lastOrNull()?.name == "version" } != null
    } else {
      return segments.asReversed()[1].name == "version"
    }
  }
}

private class BundleTablePatternCondition : PatternCondition<TomlValue>("[bundles] in TOML file") {
  override fun accepts(t: TomlValue, context: ProcessingContext?): Boolean {
    val table = t.parent.parent.parent as? TomlTable ?: return false
    val header = table.header
    return header.key?.segments?.singleOrNull()?.name == "bundles"
  }
}

private class VersionCatalogReferenceProvider(val referencesProvider: (PsiElement) -> List<TomlKeySegment>) : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is TomlLiteral) {
      return emptyArray()
    }
    val text = StringUtil.unquoteString(element.text)
    return arrayOf(VersionCatalogFileLocalReference(element, text, referencesProvider))
  }
}

/**
 * For versions that are referenced in libraries:
 * ```
 * [versions]
 * x = "123"
 * [libraries]
 * ... version.ref = "x" ...
 * ```
 * or for libraries that are referenced in bundles:
 * ```
 * [libraries]
 * x = "..."
 * [bundles]
 * a = [ "x" ]
 * ```
 */
private class VersionCatalogFileLocalReference(literal: TomlLiteral, val text: String, val provider: (PsiElement) -> List<TomlKeySegment>) : PsiReferenceBase<TomlLiteral>(literal) {
  override fun resolve(): PsiElement? {
    val entries = provider(element)
    return entries.find { it.name == text }
  }
}