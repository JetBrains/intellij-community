package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiJavaFile

class JavaMultiLineEvaluationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null
  override val language = Language.JAVA
  override val feature = "multi-line-completion"
  override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitJavaFile(file)
  }

  override fun visitCodeBlock(block: PsiCodeBlock) {
    codeFragment?.let { file ->
      val splits = MultiLineVisitorUtils.splitElementByIndents(block, JavaSupporter)
      splits.forEach { file.addChild(it) }
    }
  }

  private object JavaSupporter : MultiLineVisitorUtils.LanguageSupporter {
    override val singleLineCommentPrefix: List<String> = listOf("//")
    override val multiLineCommentPrefix: List<Pair<String, String>> = listOf("/*" to "*/")
  }
}
