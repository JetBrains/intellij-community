package com.intellij.cce.java.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeFragmentWithPsi
import com.intellij.cce.core.CodeTokenWithPsi
import com.intellij.cce.core.Language
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod

class JavaCompletionContextEvaluationVisitor: EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null
  override val language = Language.JAVA
  override val feature = "completion-context"
  override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragmentWithPsi(file.textOffset, file.textLength, file).apply { text = file.text }
    super.visitJavaFile(file)
  }

  override fun visitClass(aClass: PsiClass) {
    codeFragment?.let { file ->
      for (child in aClass.children) {
        when (child) {
          is PsiField, is PsiMethod -> {
            val start = child.textRange.startOffset
            file.addChild(CodeTokenWithPsi(child.text, start, FIELD_PROPERTIES, child))
          }
        }
      }
    }
    super.visitClass(aClass)
  }

  override fun visitCodeBlock(block: PsiCodeBlock) {
    codeFragment?.let { file ->
      val start = block.statements[0].textRange.startOffset
      val end = block.statements[block.statementCount - 1].textRange.endOffset
      val text = file.text.subSequence(start, end)
      file.addChild(CodeTokenWithPsi(text.toString(), start, METHOD_PROPERTIES, block))
    }
  }
}

private val METHOD_PROPERTIES = SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.UNKNOWN) {}
private val FIELD_PROPERTIES = SimpleTokenProperties.create(TypeProperty.FIELD, SymbolLocation.UNKNOWN) {}
