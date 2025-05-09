// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.psi.asUnderscoredIdentifier
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.psi.KtProperty


/**
 * Base class for Compose resources code insight features (e.g. find usages, rename)
 *
 * Handles the common case of differences between file-based resources and string value resources
 *
 * @see ComposeResourcesFileBase
 * @see ComposeResourcesXmlBase
 *
 */
internal interface ComposeResourcesBase {

  /** @return the file for file-based resources, strings.xml file for strings resources */
  fun getPsiFile(element: PsiElement): PsiFile?

  /** @return the name of the file for file-based resources, or the name of the property for string resources */
  fun getName(element: PsiElement): String?

  /** drawable, font dirs vs value dir */
  val validInnerComposeResourcesDirNames: Set<String>

  /**
   * Determines if the given element belongs to a Compose resources context.
   *
   * This function checks if the `element` is part of a Compose resources directory structure
   * by validating the parent directory's name and ensuring the file is located within a valid
   * Compose resources directory.
   *
   * @param element the PSI element to be examined
   * @return `true` if the element is part of a Compose resources directory, `false` otherwise
   */
  fun isComposeResourcesElement(element: PsiElement): Boolean {
    val psiFile = getPsiFile(element) ?: return false
    val parentName = psiFile.parent?.name ?: return false
    if (!parentName.isValidInnerComposeResourcesDirNameFor(validInnerComposeResourcesDirNames)) return false
    val composeResourcesDir = psiFile.parent?.parent?.virtualFile?.toNioPathOrNull() ?: return false
    return psiFile.project.getAllComposeResourcesDirs().any { it.directoryPath == composeResourcesDir }
  }

  /**
   * Retrieves a top-level Kotlin property declaration from a Compose resource.
   *
   * This method attempts to fetch a Kotlin property (`KtProperty`) that matches
   * the name derived from the provided Compose resource element. The search is
   * scoped to the current project and looks for top-level property declarations.
   *
   * @param element the PSI element associated with the Compose resource
   * @return the top-level Kotlin property (`KtProperty`) if found, or `null` if no match is found
   */
  fun getKotlinPropertyFromComposeResource(element: PsiElement): KtProperty? {
    val name = getName(element) ?: return null
    val module = element.module ?: return null
    val project = element.project
    val composeResourcePath = element.containingFile.virtualFile.toNioPathOrNull() ?: return null
    val composeResourcesDir = project.getAllComposeResourcesDirs().firstOrNull { composeResourcePath.startsWith(it.directoryPath) } ?: return null
    // custom compose resources dirs can be anywhere, so we search in the whole project
    val searchScope = if (composeResourcesDir.isCustom) GlobalSearchScope.projectScope(project) else GlobalSearchScope.moduleScope(module)
    return KotlinPropertyShortNameIndex[name, project, searchScope]
      .filterIsInstance<KtProperty>() // even though it's called KotlinPropertyShortNameIndex it returns KtNamedDeclaration
      .firstOrNull { it.isTopLevel } // todo[alexandru.resiga] from 1.8.1 there will be only one declaration with that name
  }
}

internal interface ComposeResourcesFileBase : ComposeResourcesBase {
  override fun getPsiFile(element: PsiElement): PsiFile? = element as? PsiFile
  override fun getName(element: PsiElement): String? = element.namedUnwrappedElement?.name?.withoutExtension?.asUnderscoredIdentifier()
  override val validInnerComposeResourcesDirNames: Set<String>
    get() = setOf("drawable", "font")
}

internal interface ComposeResourcesXmlBase : ComposeResourcesBase {
  override fun getPsiFile(element: PsiElement): PsiFile? = element.containingFile?.takeIf { it.language == XMLLanguage.INSTANCE }
  override fun getName(element: PsiElement): String? = element.text.asUnderscoredIdentifier()
  override val validInnerComposeResourcesDirNames: Set<String>
    get() = setOf("values")
}
