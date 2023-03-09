// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.navigation

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles
import org.toml.lang.psi.TomlKeySegment

class VersionCatalogRenameInputValidator : RenameInputValidator {

  override fun getPattern(): ElementPattern<out PsiElement> {
    return psiElement(TomlKeySegment::class.java)
      .with(object : PatternCondition<TomlKeySegment>("version ref descriptor") {
        override fun accepts(t: TomlKeySegment, context: ProcessingContext?): Boolean {
          return getVersionCatalogFiles(t.project).any { it.value == t.containingFile?.virtualFile }
        }
      })
  }

  override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean {
    return tomlIdentifierRegex.matches(newName)
  }
}

private val tomlIdentifierRegex = Regex("[0-9_\\-a-zA-Z]+")