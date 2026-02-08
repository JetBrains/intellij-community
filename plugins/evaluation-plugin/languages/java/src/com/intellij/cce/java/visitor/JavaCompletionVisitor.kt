// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.java.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.JvmProperties
import com.intellij.cce.core.Language
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiImportStaticStatement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.util.PsiTypesUtil

class JavaCompletionVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.JAVA
  override val feature: String = "token-completion"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitJavaFile(file)
  }

  override fun visitKeyword(keyword: PsiKeyword) {
    val token = CodeToken(keyword.text, keyword.textOffset, keywordProperties())
    codeFragment?.addChild(token)
  }

  override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
    expression.acceptChildren(this)
  }

  override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
    val name = reference.referenceName
    if (name != null) {
      val token = CodeToken(name, reference.textOffset, createTokenProperties(reference))
      codeFragment?.addChild(token)
    }
    super.visitReferenceElement(reference)
  }

  override fun visitLiteralExpression(expression: PsiLiteralExpression) {
    if (expression is PsiLiteralExpressionImpl &&
        (expression.type == PsiTypes.booleanType() || expression.type == PsiTypes.nullType())) {
      val token = CodeToken(expression.text, expression.textOffset, keywordProperties())
      codeFragment?.addChild(token)
    }
  }

  override fun visitPackageStatement(statement: PsiPackageStatement) = Unit
  override fun visitImportStatement(statement: PsiImportStatement) = Unit
  override fun visitImportStaticStatement(statement: PsiImportStaticStatement) = Unit
  override fun visitComment(comment: PsiComment) = Unit

  private fun createTokenProperties(reference: PsiJavaCodeReferenceElement): TokenProperties {
    return when (val def = reference.resolve()) {
             is PsiField -> fieldProperties(def)
             is PsiClass -> typeReferenceProperties(def)
             is PsiClassObjectAccessExpression ->
               PsiTypesUtil.getPsiClass(def.operand.type)?.let { typeReferenceProperties(it) }
             //            is PsiTypeCastExpression ->
             is PsiVariable -> variableProperties()
             is PsiMethod -> methodProperties(def)
             else -> null
           } ?: TokenProperties.UNKNOWN
  }

  private fun fieldProperties(field: PsiField): TokenProperties {
    return classMemberProperties(TypeProperty.FIELD, field)
  }

  private fun typeReferenceProperties(type: PsiClass): TokenProperties {
    return properties(TypeProperty.TYPE_REFERENCE, type.asLocation()) {
      packageName = getContainingPackage(type)
    }
  }

  private fun variableProperties(): TokenProperties {
    return properties(TypeProperty.VARIABLE, SymbolLocation.PROJECT) {}
  }

  private fun keywordProperties(): TokenProperties {
    return properties(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}
  }

  private fun methodProperties(method: PsiMethod?): TokenProperties {
    return classMemberProperties(TypeProperty.METHOD_CALL, method)
  }

  private fun constructorProperties(expression: PsiNewExpression): TokenProperties {
    val method = expression.resolveMethod()
    if (method != null) {
      return methodProperties(method)
    }

    val referringClass = expression.classOrAnonymousClassReference?.resolve() as? PsiClass
    if (referringClass != null) {
      return properties(TypeProperty.METHOD_CALL, referringClass.asLocation()) {
        isStatic = referringClass.hasModifierProperty(PsiModifier.STATIC)
        declaringClass = referringClass.qualifiedName
        packageName = getContainingPackage(referringClass)
      }
    }

    return properties(TypeProperty.UNKNOWN, null.asLocation()) {}
  }

  private fun classMemberProperties(tokenType: TypeProperty, member: PsiMember?): TokenProperties {
    return properties(tokenType, member.asLocation()) {
      isStatic = member?.hasModifierProperty(PsiModifier.STATIC)
      declaringClass = member?.containingClass?.qualifiedName
      packageName = getContainingPackage(member)
    }
  }

  private fun properties(tokenType: TypeProperty, location: SymbolLocation, init: JvmProperties.Builder.() -> Unit)
    : TokenProperties {
    return JvmProperties.create(tokenType, location) {
      init(this)
    }
  }

  private fun PsiElement?.asLocation(): SymbolLocation {
    if (this == null) return SymbolLocation.UNKNOWN
    val containingFile = this.containingFile

    val virtualFile = containingFile.virtualFile
    // Handle access to array.length and array.clone()
    if (containingFile != null && virtualFile == null) {
      return if (this is PsiField && name == "length" || this is PsiMethod && name == "clone")
        SymbolLocation.LIBRARY
      else {
        SymbolLocation.UNKNOWN
      }
    }

    val isInLibrary = ProjectFileIndex.getInstance(project).isInLibrary(virtualFile)
    return if (isInLibrary) SymbolLocation.LIBRARY else SymbolLocation.PROJECT
  }

  private fun getContainingPackage(resolvedReference: PsiElement?): String? {
    return (resolvedReference?.containingFile as? PsiClassOwner)?.packageName
  }
}