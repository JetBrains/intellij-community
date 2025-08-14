// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.findUsages

import com.intellij.compose.ide.plugin.resources.ComposeResourcesBase
import com.intellij.compose.ide.plugin.resources.ComposeResourcesFileBase
import com.intellij.compose.ide.plugin.resources.ComposeResourcesXmlBase
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageTargetProvider

/** Display usages of Compose resources from resource files and string values */
abstract class ComposeResourcesFindUsagesHandlerBaseFactory : FindUsagesHandlerFactory(), ComposeResourcesBase {

  override fun canFindUsages(element: PsiElement): Boolean = isComposeResourcesElement(element)

  override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
    if (forHighlightUsages) return null
    val property = runReadAction { getKotlinPropertyFromComposeResource(element) } ?: return null
    return object : FindUsagesHandler(property) {
      override fun getPrimaryElements(): Array<PsiElement> = arrayOf(myPsiElement)
    }
  }
}

class ComposeResourcesFileFindUsagesHandlerFactory : ComposeResourcesFindUsagesHandlerBaseFactory(), ComposeResourcesFileBase

class ComposeResourcesXmlFindUsagesHandlerFactory : ComposeResourcesFindUsagesHandlerBaseFactory(), ComposeResourcesXmlBase

class ComposeResourcesUsagesTargetProvider : UsageTargetProvider, ComposeResourcesXmlBase {
  override fun getTargets(editor: Editor, file: PsiFile): Array<out UsageTarget?>? {
    val element = file.findElementAt(editor.caretModel.offset) ?: return null
    return element.takeIf { isComposeResourcesElement(it) }?.let { arrayOf(PsiElement2UsageTargetAdapter(it, true)) }
  }
}