// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.navigation

import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.toml.getVersions
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlValue

class VersionCatalogReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
      registrar.registerReferenceProvider(versionRefPattern, VersionCatalogReferenceProvider())
  }
}

internal val refKeyValuePattern: PsiElementPattern.Capture<TomlKeyValue> = PlatformPatterns
  .psiElement(TomlKeyValue::class.java)
  .with(RefPatternCondition())
internal val versionRefPattern: ElementPattern<TomlValue> = PlatformPatterns
  .psiElement(TomlValue::class.java)
  .withParent(refKeyValuePattern)

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

private class VersionCatalogReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is TomlLiteral) {
      return emptyArray()
    }
    val text = StringUtil.unquoteString(element.text)
    return arrayOf(VersionCatalogVersionReference(element, text))
  }

  private class VersionCatalogVersionReference(literal: TomlLiteral, val text: String) : PsiReferenceBase<TomlLiteral>(literal) {
    override fun resolve(): PsiElement? {
      val versions = getVersions(element)
      return versions.find { it.name == text }
    }
  }

}