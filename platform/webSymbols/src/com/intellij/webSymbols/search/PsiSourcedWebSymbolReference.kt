package com.intellij.webSymbols.search

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPointerManager
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.refactoring.suggested.startOffset
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory

class PsiSourcedWebSymbolReference(private val symbol: WebSymbol,
                                   private val sourceElement: PsiElement,
                                   private val host: PsiExternalReferenceHost,
                                   private val range: TextRange) : PsiReference {

  internal val newName: Ref<String> = Ref()

  fun createRenameHandler() =
    RenameHandler(this)

  override fun getElement(): PsiElement =
    host

  override fun getRangeInElement(): TextRange =
    range

  override fun resolve(): PsiElement =
    sourceElement

  override fun getCanonicalText(): String =
    (sourceElement as? PsiNamedElement)?.name
    ?: range.substring(host.text)

  override fun handleElementRename(newElementName: String): PsiElement {
    newName.set(newElementName)
    return host
  }

  override fun bindToElement(element: PsiElement): PsiElement =
    element

  override fun isReferenceTo(element: PsiElement): Boolean =
    element.isEquivalentTo(sourceElement) || sourceElement.isEquivalentTo(element)

  override fun isSoft(): Boolean =
    false

  class RenameHandler(reference: PsiSourcedWebSymbolReference) {
    private val symbol = reference.symbol
    private val targetPointer = reference.resolve().createSmartPointer()
    private val rangePointer = SmartPointerManager.getInstance(reference.element.project).createSmartPsiFileRangePointer(
      reference.element.containingFile, reference.rangeInElement.shiftRight(reference.element.startOffset)
    )
    private val nameRef = reference.newName

    fun handleRename(): NonCodeUsageInfo? {
      val range = rangePointer.psiRange ?: return null
      val file = rangePointer.element ?: return null
      val newName = nameRef.get() ?: return null
      val target = targetPointer.dereference() ?: return null
      return NonCodeUsageInfo.create(file, range.startOffset, range.endOffset, target,
                                     symbol.adjustNameForRefactoring(
                                       WebSymbolsQueryExecutorFactory.create(file),
                                       newName,
                                       file.text.substring(range.startOffset, range.endOffset)))
    }

  }

}