// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.*
import com.intellij.psi.util.startOffset

class JavaFunctionCallingVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private lateinit var codeFragment: CodeFragment
  private lateinit var fileName: String

  private var className: String? = null

  override val language: Language = Language.JAVA
  override val feature: String = "function-calling"

  override fun getFile(): CodeFragment =
    if (this::codeFragment.isInitialized) codeFragment else throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    fileName = file.name
    super.visitJavaFile(file)
  }

  override fun visitPackageStatement(statement: PsiPackageStatement) {
    val properties = SimpleTokenProperties.create(TypeProperty.FILE, SymbolLocation.PROJECT) {
      put(TokenLocationProperty.FILE.key, fileName)
    }
    codeFragment.addChild(
      CodeToken(statement.text, statement.startOffset, properties)
    )
  }

  override fun visitClass(aClass: PsiClass) {
    if (aClass.isInterface || aClass.isEnum || aClass.isAnnotationType) {
      return
    }
    aClass.nameIdentifier?.let {
      val properties = SimpleTokenProperties.create(TypeProperty.CLASS, SymbolLocation.PROJECT) {
        put(TokenLocationProperty.FILE.key, fileName)
        put(TokenLocationProperty.CLASS.key, it.text)
      }
      codeFragment.addChild(CodeToken(it.text, it.startOffset, properties))

      val prevClassName = className
      className = it.text
      super.visitClass(aClass)
      className = prevClassName
    }
  }

  override fun visitMethod(method: PsiMethod) {
    check(className != null)

    if (method.isConstructor) {
      return
    }

    method.nameIdentifier?.let { identifier ->
      val properties = SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {
        put(TokenLocationProperty.FILE.key, fileName)
        put(TokenLocationProperty.CLASS.key, className!!)
        put(TokenLocationProperty.METHOD.key, identifier.text)
      }

      codeFragment.addChild(CodeToken(identifier.text, identifier.startOffset, properties))
    }
  }
}