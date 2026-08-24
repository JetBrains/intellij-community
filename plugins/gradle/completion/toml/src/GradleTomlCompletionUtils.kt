// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.toml

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

internal const val DEFAULT_VERSION_CATALOG_NAME: String = "libs.versions.toml"
internal const val LIBRARIES_HEADER = "[libraries]"
internal val DEFAULT_VERSION_CATALOG_NAME_FILE_PATTERN = psiFile().withName(DEFAULT_VERSION_CATALOG_NAME)

internal inline fun <reified I : PsiElement> psiElement(): PsiElementPattern.Capture<I> {
  return psiElement(I::class.java)
}

internal fun insideLibrariesTable() =
  psiElement()
    //.inFile(DEFAULT_VERSION_CATALOG_NAME_FILE_PATTERN)
    .inside(
      psiElement<TomlTable>()
        .withChild(
          psiElement<TomlTableHeader>()
            .withText(LIBRARIES_HEADER)
        )
    )

internal fun TomlKey.isDirectlyInLibrariesTable(): Boolean {
  val parentTable = this.parent.parent as? TomlTable ?: return false
  return parentTable.header.text == LIBRARIES_HEADER
}

internal fun TomlLiteral.getParentKeyValue(): TomlKeyValue? {
  return this.parent as? TomlKeyValue
}

internal fun TomlKeyValue.getParentInlineTable(): TomlInlineTable? {
  return this.parent as? TomlInlineTable
}

internal fun TomlLiteral.getTomlKey(): TomlKey? {
  return this.getParentKeyValue()?.key
}

internal fun TomlKey.getLastSegmentName(): String {
  return this.text.substringAfterLast(".")
}

internal fun TomlLiteral.getSiblingValue(siblingKey: String): String {
  val siblingKeyValue = this.getParentKeyValue()?.getParentInlineTable()?.children?.firstOrNull {
    it is TomlKeyValue && it.key.text == siblingKey
  } as? TomlKeyValue ?: return ""
  return siblingKeyValue.value?.text?.removeWrappingQuotes() ?: ""
}

/**
 * Strips the quotes of a string literal's text.
 *
 * The closing quote is optional: while a coordinate is being typed the literal under the caret is often still
 * unterminated (`"my`), and the opening quote must not leak into the completion prefix — it would both offset
 * the auto-popup character threshold and be sent to the dependency search as part of the query.
 */
internal fun String.removeWrappingQuotes(): String {
  val quote = firstOrNull()?.takeIf { it == '"' || it == '\'' } ?: return this
  val end = if (length > 1 && this[length - 1] == quote) length - 1 else length
  return substring(1, end)
}