// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.rename

import com.intellij.compose.ide.plugin.resources.getAllComposeResourcesDirs
import com.intellij.compose.ide.plugin.resources.isValidInnerComposeResourcesDirName
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.rename.RenamePsiFileProcessor
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinPropertyProcessor
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.isPublic

/**
 * Rename Compose resource files and propagate to code usages
 *
 * Extracts the specific Compose resource property from [KotlinPropertyShortNameIndex]
 * and delegates the rename to [RenameKotlinPropertyProcessor]
 * */
internal class ComposeResourcesFileRenameProcessor : RenamePsiFileProcessor() {
  override fun canProcessElement(element: PsiElement): Boolean {
    val psiFile = element as? PsiBinaryFile ?: return false
    val parentName = psiFile.parent?.name ?: return false
    if (!parentName.isValidInnerComposeResourcesDirName) return false
    val composeResourcesDir = psiFile.parent?.parent?.virtualFile ?: return false
    return element.project.getAllComposeResourcesDirs().any { it.directoryPath == composeResourcesDir.toNioPath() }
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    val project = element.project
    val projectScope = GlobalSearchScope.projectScope(project)
    val nameWithoutExtension = element.namedUnwrappedElement?.name?.withoutExtension ?: return
    val declaration = KotlinPropertyShortNameIndex[nameWithoutExtension, project, projectScope].firstOrNull { !it.isPublic } ?: return
    val property = declaration as? KtProperty ?: return
    val newNameWithoutExtension = newName.withoutExtension
    RenameKotlinPropertyProcessor().prepareRenaming(property, newNameWithoutExtension, allRenames)
  }
}

private val String.withoutExtension: String get() = substringBeforeLast(".")