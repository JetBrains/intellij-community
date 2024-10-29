// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorBase
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject

class KotlinCompletionEvaluationVisitor : EvaluationVisitor, KtTreeVisitorVoid() {
  private var _codeFragment: CodeFragment? = null

  override val feature: String = "token-completion"
  override val language: Language = Language.KOTLIN

  override fun getFile(): CodeFragment = _codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitKtFile(file: KtFile) {
    _codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitKtFile(file)
  }

  override fun visitElement(element: PsiElement) {
    val name = element.text
    if (KtTokens.KEYWORDS.contains(element.elementType) || KtTokens.SOFT_KEYWORDS.contains(element.elementType)) {
      val token = CodeToken(name, element.textOffset, keywordProperties())
      _codeFragment?.addChild(token)
    }
    super.visitElement(element)
  }

  override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
    if (expression !is KtOperationReferenceExpression) {
      val name = expression.getReferencedName()
      val token = CodeToken(name, expression.textOffset, createNodeProperties(expression))
      _codeFragment?.addChild(token)
    }
    super.visitSimpleNameExpression(expression)
  }

  override fun visitPackageDirective(directive: KtPackageDirective) = Unit
  override fun visitPackageDirective(directive: KtPackageDirective, data: Void?): Void? = null
  override fun visitImportList(importList: KtImportList) = Unit
  override fun visitImportAlias(importAlias: KtImportAlias, data: Void?): Void? = null
  override fun visitImportAlias(importAlias: KtImportAlias) = Unit
  override fun visitImportDirective(importDirective: KtImportDirective) = Unit
  override fun visitStringTemplateEntry(entry: KtStringTemplateEntry) = Unit
  override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) = Unit
  override fun visitComment(comment: PsiComment) = Unit

  private fun createNodeProperties(expression: KtSimpleNameExpression): TokenProperties {
    val context = expression.analyze()
    return when (val descriptor = context[BindingContext.REFERENCE_TARGET, expression]) {
             is FunctionDescriptor -> classMemberProperties(TypeProperty.METHOD_CALL, descriptor)
             is PropertyDescriptor -> classMemberProperties(TypeProperty.FIELD, descriptor)
             is ClassDescriptorBase -> typeProperties(descriptor)
             is ValueParameterDescriptor -> properties(TypeProperty.VARIABLE) {}
             is LocalVariableDescriptor -> properties(TypeProperty.VARIABLE) {}
             else -> null
           } ?: TokenProperties.UNKNOWN
  }

  private fun keywordProperties() = properties(TypeProperty.KEYWORD) {}

  private fun classMemberProperties(tokenType: TypeProperty, descriptor: DeclarationDescriptor): TokenProperties {
    return properties(tokenType) {
      isStatic = isStatic(descriptor)
      packageName = getContainingPackage(descriptor)
    }
  }

  private fun typeProperties(descriptor: DeclarationDescriptor): TokenProperties {
    return properties(TypeProperty.TYPE_REFERENCE) {
      packageName = getContainingPackage(descriptor)
    }
  }

  private fun properties(tokenType: TypeProperty, init: JvmProperties.Builder.() -> Unit)
    : TokenProperties {
    return JvmProperties.create(tokenType, SymbolLocation.UNKNOWN) {
      init(this)
    }
  }

  private fun isStatic(descriptor: DeclarationDescriptor): Boolean {
    val declaration = descriptor.containingDeclaration
    if (declaration is ClassDescriptor) {
      if (declaration.kind == ClassKind.OBJECT) return true
    }
    if (declaration?.isCompanionObject() == true) return true
    val psi = descriptor.findPsi()
    if (psi is PsiMember) return psi.hasModifierProperty(PsiModifier.STATIC)
    return false
  }

  private fun getContainingPackage(descriptor: DeclarationDescriptor): String? {
    return try {
      descriptor.findPackage().fqName.asString()
    }
    catch (e: Throwable) {
      null
    }
  }
}