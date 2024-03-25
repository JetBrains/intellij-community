package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import java.nio.file.Paths


class JavaTestGenerationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  override val feature: String = "test-generation"
  override val language: Language = Language.JAVA
  private lateinit var testFile: PsiJavaFile
  private var codeFragment: CodeFragment? = null

  override fun getFile(): CodeFragment = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)

    getTestFileForClass(file).let { if(it != null) {
        testFile = it
        super.visitJavaFile(file)
      }
    }
  }

  private fun getTestFileForClass(file: PsiJavaFile): PsiJavaFile? {
    val scope = GlobalSearchScope.projectScope(file.project)
    val files = FilenameIndex.getVirtualFilesByName(file.name.replace(".java", "Test.java"), scope)

    if (files.isEmpty()) return null
    return convertVirtualFileToJavaPsiFile(file.project, files.first())
  }

  private fun convertVirtualFileToJavaPsiFile(project: Project, file: VirtualFile): PsiJavaFile {
    val psiManager = PsiManager.getInstance(project)
    val psiFile = psiManager.findFile(file)

    return psiFile as PsiJavaFileImpl
  }

  override fun visitMethod(method: PsiMethod) {
    if (TestFrameworks.getInstance().isTestMethod(method)) {
      return
    }
    val testMethod = findTestMethodInFile(testFile, method.name)
    val testMethodCopy = testMethod?.copy() as? PsiMethod

    if (testMethodCopy != null) {
      removeComments(testMethodCopy)
      val init: MutableMap<String, String>.() -> Unit = {
        this["unitTest"] = testMethodCopy.text
        this["testPath"] = method.project.basePath?.let { Paths.get(it).relativize(Paths.get(testMethod.containingFile.virtualFile.path)) }.toString()
        this["testOffset"] = testMethod.textRange.startOffset.toString()
      }
      codeFragment?.addChild(CodeToken(method.text, method.textRange.startOffset, getMethodProperties(init)))
    }
  }

  private fun removeComments(method: PsiMethod) {
    for (comment in method.descendantsOfType<PsiComment>().toList()) {
      if (comment.isValid) {
        comment.delete()
      }
    }
  }

  private fun findTestMethodInFile(file: PsiJavaFile, methodName: String) : PsiMethod? {
    for (fileClass: PsiClass in file.classes) {
      for (child: PsiMethod in PsiTreeUtil.findChildrenOfType(fileClass, PsiMethod::class.java)){
        if (TestFrameworks.getInstance().isTestMethod(child) && child.name.replace("Test", "") == methodName) {
          return child
        }
      }
    }
    return null
  }

  private fun getMethodProperties(init: MutableMap<String, String>.() -> Unit): TokenProperties{
    return SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.UNKNOWN, init)
  }
}
