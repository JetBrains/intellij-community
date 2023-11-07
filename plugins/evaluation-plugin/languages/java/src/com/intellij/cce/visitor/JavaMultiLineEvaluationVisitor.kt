package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.*

class JavaMultiLineEvaluationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language = Language.JAVA

  override val feature = "multi-line-completion"

  override fun getFile(): CodeFragment {
    return codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")
  }

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitJavaFile(file)
  }

  //override fun visitClass(aClass: PsiClass) {
  //  codeFragment?.let { file ->
  //    if (aClass.children.isNotEmpty()) {
  //      val start = aClass.firstChild.textRange.startOffset
  //      val end = aClass.lastChild.textRange.endOffset
  //      val text = file.text.subSequence(start, end)
  //      file.addChild(CodeToken(text.toString(), start, METHOD_PROPERTIES))
  //    } else {
  //      file.addChild(CodeToken(aClass.text, aClass.textOffset, METHOD_PROPERTIES))
  //    }
  //  }
  //  super.visitClass(aClass)
  //}

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
