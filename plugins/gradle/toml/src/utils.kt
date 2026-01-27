// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toml

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import org.toml.lang.psi.*

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

internal fun String.removeWrappingQuotes(): String {
  val s = this
  return if (s.length >= 2 &&
             ((s.startsWith('"') && s.endsWith('"')) ||
              (s.startsWith('\'') && s.endsWith('\'')))) {
    s.substring(1, s.length - 1)
  } else s
}