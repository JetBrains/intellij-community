package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.*

class JavaMultiLineEvaluationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null
  override val language = Language.JAVA
  override val feature = "multi-line-completion"
  override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitJavaFile(file)
  }

  override fun visitClass(aClass: PsiClass) {
    //codeFragment?.let { file ->
    //  for (child in aClass.children) {
    //    when (child) {
    //      is PsiField, is PsiMethod -> {
    //        val start = child.textRange.startOffset
    //        file.addChild(CodeToken(child.text, start, FIELD_PROPERTIES))
    //      }
    //    }
    //  }
    //}
    super.visitClass(aClass)
  }

  override fun visitCodeBlock(block: PsiCodeBlock) {
    codeFragment?.let { file ->
      val start = block.statements[0].textRange.startOffset
      val end = block.statements[block.statementCount - 1].textRange.endOffset
      val text = file.text.subSequence(start, end)
      file.addChild(CodeToken(text.toString(), start, METHOD_PROPERTIES))
    }
  }
}

private val METHOD_PROPERTIES = SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.UNKNOWN) {}
private val FIELD_PROPERTIES = SimpleTokenProperties.create(TypeProperty.FIELD, SymbolLocation.UNKNOWN) {}
