package com.intellij.webSymbols.search

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PsiSourcedWebSymbolReference(
  private val symbol: WebSymbol,
  private val sourceElement: PsiElement,
  private val host: PsiExternalReferenceHost,
  private val range: TextRange,
) : PsiReference {

  internal val newName: Ref<String> = Ref()

  fun createRenameHandler(): RenameHandler =
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
    private val targetPointer = reference.resolve()
      .let { if (it is FakePsiElement) it.context ?: it else it }
      .createSmartPointer()
    private val rangePointer = SmartPointerManager.getInstance(reference.element.project).createSmartPsiFileRangePointer(
      reference.element.containingFile, reference.rangeInElement.shiftRight(reference.element.startOffset)
    )
    private val nameRef = reference.newName

    fun handleRename(): NonCodeUsageInfo? {
      val range = rangePointer.psiRange ?: return null
      val file = rangePointer.element ?: return null
      val newName = nameRef.get() ?: return null
      val target = targetPointer.dereference() ?: return null
      val queryExecutor = WebSymbolsQueryExecutorFactory.create(
        PsiTreeUtil.findElementOfClassAtRange(file, range.startOffset, range.endOffset, PsiElement::class.java)
        ?: file
      )
      return NonCodeUsageInfo.create(file, range.startOffset, range.endOffset, target,
                                     symbol.adjustNameForRefactoring(
                                       queryExecutor,
                                       newName,
                                       file.text.substring(range.startOffset, range.endOffset)))
    }
  }
}