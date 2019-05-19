// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.psi.util

import com.intellij.lang.ASTFactory
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.IgnoreSettingsType
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileConstants.NEWLINE
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreLanguage
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.GeneratedMarkerVisitor
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.TestOnly

@TestOnly
fun updateIgnoreBlock(project: Project,
                      ignoreFile: VirtualFile,
                      ignoredGroupDescription: String,
                      vararg newEntries: IgnoredFileDescriptor): PsiFile? {
  val ignoreFilePsi = ignoreFile.findIgnorePsi(project) ?: return null
  val psiFactory = PsiFileFactory.getInstance(project) as PsiFileFactoryImpl
  val psiParserFacade = PsiParserFacade.SERVICE.getInstance(project)
  invokeAndWaitIfNeeded {
    runUndoTransparentWriteAction {
      updateIgnoreBlock(psiParserFacade, ignoreFilePsi, ignoredGroupDescription,
                        newEntries.map { it.toPsiElement(psiFactory, ignoreFilePsi) })
    }
    ignoreFile.save()
  }
  return ignoreFilePsi
}

fun addNewElementsToIgnoreBlock(project: Project,
                                ignoreFile: VirtualFile,
                                ignoredGroupDescription: String,
                                vararg newEntries: IgnoredFileDescriptor): PsiFile? {
  val ignoreFilePsi = ignoreFile.findIgnorePsi(project) ?: return null
  val psiFactory = PsiFileFactory.getInstance(project) as PsiFileFactoryImpl
  val psiParserFacade = PsiParserFacade.SERVICE.getInstance(project)
  invokeAndWaitIfNeeded {
    runUndoTransparentWriteAction {
      addNewElementsToIgnoreBlock(ignoredGroupDescription, psiParserFacade, ignoreFilePsi,
                                  newEntries.map {
                                    it.toPsiElement(psiFactory, ignoreFilePsi)
                                  })
    }
    ignoreFile.save()
  }
  return ignoreFilePsi
}

fun addNewElements(project: Project, ignoreFile: VirtualFile, vararg newEntries: IgnoredFileDescriptor): PsiFile? {
  val ignoreFilePsi = ignoreFile.findIgnorePsi(project) ?: return null
  val psiFactory = PsiFileFactory.getInstance(project) as PsiFileFactoryImpl
  invokeAndWaitIfNeeded {
    runUndoTransparentWriteAction {
      addNewElements(ignoreFilePsi,
                     newEntries.map {
                       it.toPsiElement(psiFactory, ignoreFilePsi)
                     })
    }
    ignoreFile.save()
  }
  return ignoreFilePsi
}

private fun updateIgnoreBlock(psiParserFacade: PsiParserFacade,
                              ignoreFilePsi: PsiFile,
                              ignoredGroupDescription: String,
                              newEntries: List<PsiElement>) {
  val ignoredGroupPsiElement = ignoreFilePsi.findOrCreateIgnoreBlockDescriptionPsi(ignoredGroupDescription, psiParserFacade)

  var replacementCandidate = ignoredGroupPsiElement.nextIgnoreGroupElement()
  var lastElementInBlock = if (ignoredGroupPsiElement.nextSibling.isNewLine()) ignoredGroupPsiElement.nextSibling else ignoredGroupPsiElement

  for (newEntry in newEntries) {
    if (replacementCandidate != null) {
      lastElementInBlock = replacementCandidate.replace(newEntry)
    }
    else {
      val newLinePsi = lastElementInBlock.createNewline()
      lastElementInBlock = ignoreFilePsi.addAfter(newLinePsi, lastElementInBlock)
      lastElementInBlock = ignoreFilePsi.addAfter(newEntry, lastElementInBlock)
    }
    replacementCandidate = replacementCandidate.nextIgnoreGroupElement()
  }
}

private fun addNewElements(ignoreFilePsi: PsiFile, newEntries: List<PsiElement>) {
  with(ignoreFilePsi) {
    if (!lastChild.isNewLine()) {
      add(createNewline())
    }
    newEntries.forEach { add(it); add(createNewline()) }
  }
}

private fun addNewElementsToIgnoreBlock(ignoredGroupDescription: String,
                                        psiParserFacade: PsiParserFacade,
                                        ignoreFilePsi: PsiFile,
                                        newEntries: List<PsiElement>) {
  val ignoredGroupPsiElement = ignoreFilePsi.findOrCreateIgnoreBlockDescriptionPsi(ignoredGroupDescription, psiParserFacade)

  var nextIgnoreGroupElement = ignoredGroupPsiElement.nextIgnoreGroupElement()
  var lastElementInBlock = if (ignoredGroupPsiElement.nextSibling.isNewLine()) ignoredGroupPsiElement.nextSibling else ignoredGroupPsiElement
  val existingElements = mutableListOf<PsiElement>()

  while (nextIgnoreGroupElement != null) {
    val existingElement = newEntries.find { nextIgnoreGroupElement?.textMatches(it) == true }
    if (existingElement != null) {
      existingElements.add(existingElement)
    }
    lastElementInBlock = nextIgnoreGroupElement
    nextIgnoreGroupElement = nextIgnoreGroupElement.nextIgnoreGroupElement()
  }

  for (elementToAdd in newEntries - existingElements) {
    val newLinePsi = lastElementInBlock.createNewline()
    lastElementInBlock = ignoreFilePsi.addAfter(newLinePsi, lastElementInBlock)
    lastElementInBlock = ignoreFilePsi.addAfter(elementToAdd, lastElementInBlock)
  }
}

private fun IgnoredFileDescriptor.toPsiElement(psiFactory: PsiFileFactoryImpl, ignorePsi: PsiFile): PsiElement {
  val ignorePath = path
  val ignoreMask = mask

  val text =
    if (ignorePath != null) {
      val ignoreFileContainingDirPath = ignorePsi.virtualFile?.parent?.path ?: throw IllegalStateException(
        "Cannot determine ignore file path for $ignorePsi")
      "/${FileUtil.getRelativePath(ignoreFileContainingDirPath, ignorePath, '/')}"
    }
    else ignoreMask ?: throw IllegalStateException("IgnoredFileBean: path and mask cannot be null at the same time")

  return ignorePsi.createElementFromText(text,
                                         when (type) {
                                           IgnoreSettingsType.UNDER_DIR -> IgnoreTypes.ENTRY_DIRECTORY
                                           IgnoreSettingsType.FILE -> IgnoreTypes.ENTRY_FILE
                                           IgnoreSettingsType.MASK -> IgnoreTypes.ENTRY
                                         }, psiFactory)
}

private fun VirtualFile.findIgnorePsi(project: Project): PsiFile? =
  runReadAction { PsiManager.getInstance(project).findFile(this).takeIf { it?.language is IgnoreLanguage } }

private fun PsiFile.findOrCreateIgnoreBlockDescriptionPsi(ignoredGroupDescription: String, psiParserFacade: PsiParserFacade): PsiElement {
  return PsiTreeUtil.findChildrenOfType(this, PsiComment::class.java)
           .firstOrNull { it.text.contains(ignoredGroupDescription) }
         ?: createIgnoreBlock(ignoredGroupDescription, psiParserFacade)
}

private fun PsiFile.createIgnoreBlock(ignoredGroupDescription: String, psiParserFacade: PsiParserFacade): PsiElement {
  if (!lastChild.isNewLine()) {
    add(createNewline())
  }
  return add(psiParserFacade.createLineOrBlockCommentFromText(language, ignoredGroupDescription))
}

private fun PsiElement.createNewline(): PsiElement {
  val holderElement = DummyHolderFactory.createHolder(PsiManager.getInstance(project), this).treeElement
  val newElement = ASTFactory.leaf(IgnoreTypes.CRLF, holderElement.charTable.intern(NEWLINE))
  holderElement.rawAddChildren(newElement)
  GeneratedMarkerVisitor.markGenerated(newElement.psi)
  return newElement.psi
}

private fun PsiElement.createElementFromText(text: String, type: IElementType, psiFactory: PsiFileFactoryImpl) =
  psiFactory.createElementFromText(text, language, type, this)
  ?: throw IllegalStateException("Cannot create PSI element for $text, $type, $this")

private fun PsiElement?.nextIgnoreGroupElement(): PsiElement? {
  var next = this?.nextSibling

  while (next != null && next.isNewLine()) {
    if (next is PsiComment) return null
    if (next.nextSibling is PsiComment) return null

    next = next.nextSibling
  }
  return next
}

private fun PsiElement?.isNewLine() = this?.text?.contains(NEWLINE) ?: false

private fun VirtualFile.save() =
  runWriteAction {
    if (isDirectory || !isValid) {
      return@runWriteAction
    }
    val documentManager = FileDocumentManager.getInstance()
    if (documentManager.isFileModified(this)) {
      documentManager.getDocument(this)?.let(documentManager::saveDocumentAsIs)
    }
  }
