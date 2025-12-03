// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.completion

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

internal const val DEFAULT_VERSION_CATALOG_NAME: String = "libs.versions.toml"

internal val DEFAULT_VERSION_CATALOG_NAME_FILE_PATTERN = psiFile().withName(DEFAULT_VERSION_CATALOG_NAME)

internal inline fun <reified I : PsiElement> psiElement(): PsiElementPattern.Capture<I> {
    return psiElement(I::class.java)
}

internal fun insideLibrariesTable() =
    psiElement()
        .inFile(DEFAULT_VERSION_CATALOG_NAME_FILE_PATTERN)
        .inside(
            psiElement<TomlTable>()
                .withChild(
                    psiElement<TomlTableHeader>()
                        .withText("[libraries]")
                )
        )

internal fun PsiElement.isTomlValue(): Boolean {
    return this.parent is TomlLiteral
}

internal fun PsiElement.getTomlKey(): String {
    return (this.parent.parent as? TomlKeyValue)?.key?.text ?: ""
}