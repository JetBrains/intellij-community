// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.findUsages

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlKeySegment

class GradleVersionCatalogFindUsagesFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean {
    return element is TomlKeySegment
  }

  override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
    if (forHighlightUsages) {
      return FindUsagesHandler.NULL_HANDLER
    }
    return GradleVersionCatalogFindUsagesHandler(element as TomlKeySegment)
  }
}