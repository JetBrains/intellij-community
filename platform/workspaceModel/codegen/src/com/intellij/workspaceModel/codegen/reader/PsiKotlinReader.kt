// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.patcher

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.annotations.Open
import com.intellij.workspaceModel.codegen.deft.model.*
import com.intellij.workspaceModel.codegen.deft.model.KtAnnotation
import com.intellij.workspaceModel.codegen.deft.model.KtConstructor
import com.intellij.workspaceModel.codegen.deft.model.KtFile
import com.intellij.workspaceModel.deft.api.annotations.Default
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.startsWithComment

class PsiKotlinReader(val file: KtFile) {
  val ktFile = PsiManager.getInstance(file.module.project!!).findFile(file.virtualFile!!)!! as org.jetbrains.kotlin.psi.KtFile
  val text: CharSequence = file.content()
  var pos = 0
  val c get() = text[pos]

  var leafScope: KtScope = file.scope
  var leafBlock: KtBlock = file.block
  var leafConstructor: KtConstructor? = null

  private val src = file.asSrc()

  fun read() {
    head()
    blockContents(ktFile)
  }

  fun head() {
    var pkg: String? = null
    var importsStart = 0
    var importsEnd = 0

    val packageDirective = ktFile.packageDirective
    if (packageDirective != null) {
      importsEnd = packageDirective.textRange.endOffset
      pkg = packageDirective.qualifiedName
    }
    file.setPackage(pkg)


    val imports = mutableSetOf<String>()
    val importList = ktFile.importList
    if (importList != null) {
      importList.imports.forEach { ktImportDirective ->
        val importPath = ktImportDirective.importPath?.pathStr
        if (importPath != null) imports.add(importPath)
      }
      importsStart = importList.textRange.startOffset
      importsEnd = importList.textRange.endOffset
    }
    file.setImports(KtImports(importsStart..importsEnd, imports))
  }

  fun blockContents(psiBlock: PsiElement) {
    var psiElement = psiBlock.firstChild
    while (psiElement != null) {
      when (psiElement) {
        is KtClass -> {
          when {
            psiElement.isEnum() -> `interface`(psiElement, WsEnum)
            psiElement.isData() -> `interface`(psiElement, WsData)
            psiElement.isSealed() -> `interface`(psiElement, WsSealed)
            psiElement.isInterface() -> `interface`(psiElement)
            else -> {

            }
          }
        }
        is KtObjectDeclaration -> {
          if (!psiElement.isCompanion())
            `interface`(psiElement, WsObject)
        }
        is KtProperty -> {
          if (!isInsideGenerateBlock()) `val`(psiElement)
        }
        is PsiComment -> generatedCodeRegion(psiElement)
        else -> {
          if (psiElement.startsWithComment()) generatedCodeRegion(psiElement.firstChild as PsiComment)
        }
      }
      psiElement = psiElement.nextSibling
    }
  }

  fun `interface`(ktClass: KtClassOrObject, predefinedInterfaceKind: KtInterfaceKind? = null) {
    val nameRange = ktClass.nameIdentifier?.textRange ?: return
    val src = Src(ktClass.name!!) { ktClass.containingFile.text }
    val name = SrcRange(src, nameRange.startOffset until nameRange.endOffset)

    if (ktClass.startsWithComment())
      generatedCodeRegion(ktClass.firstChild as PsiComment)
    val constructor = maybePrimaryConstructor(ktClass)
    val ktTypes = ktClass.superTypeListEntries.mapNotNull { type(it.typeReference) }

    val outer = leafScope
    val innerIface = KtInterface(file.module, outer, name, ktTypes, constructor, predefinedInterfaceKind, `annotation`(ktClass.annotationEntries, ktClass))
    val inner = innerIface.scope
    outer.def(name.text, inner)
    leafScope = inner
    innerIface.body = maybeBlock(ktClass, inner)
    leafScope = outer
  }

  fun type(ktTypeReference: KtTypeReference?): KtType? {
    if (ktTypeReference == null) return null
    val ktAnnotations = `annotation`(ktTypeReference.annotationEntries, ktTypeReference)

    val typeElement = ktTypeReference.typeElement
    when (typeElement) {
      is KtUserType -> {
        val ktTypes = typeElement.typeArguments.mapNotNull { ktTypeProjection -> type(ktTypeProjection.typeReference) }
        val range = typeElement.referenceExpression?.srcRange ?: typeElement.srcRange
        return KtType(range, optional = false, args = ktTypes, annotations = ktAnnotations.list)
      }
      is KtNullableType -> {
        val innerType = typeElement.innerType
        if (innerType == null) return null
        val ktTypes: List<KtType>
        val classifierRange: SrcRange
        if (innerType is KtUserType) {
          ktTypes = innerType.typeArguments.mapNotNull { ktTypeProjection -> type(ktTypeProjection.typeReference) }
          classifierRange = innerType.referenceExpression?.srcRange ?: innerType.srcRange
        } else {
          ktTypes = listOf()
          classifierRange = innerType.srcRange
        }
        return KtType(classifierRange, args = ktTypes, optional = true, annotations = ktAnnotations.list)
      }
    }
    return null
  }

  fun maybeBlock(ktClass: KtClassOrObject, iface: KtScope? = null): KtBlock {
    val outer = leafBlock
    val classBody = ktClass.body
    if (classBody == null) {
      // Class has an empty body and the source skips curly braces
      val inner = KtBlock(src, outer, isStub = true, scope = iface)
      outer.children.add(inner)
      return inner
    }

    val inner = KtBlock(src, outer, scope = iface)
    outer.children.add(inner)
    leafBlock = inner
    blockContents(classBody)
    //ktClass.getProperties().forEach { ktProperty -> `val`(ktProperty) }
    inner.range = range(classBody)
    //PsiTreeUtil.findChildrenOfType(ktClass, PsiComment::class.java).forEach { psiComment -> generatedCodeRegion(psiComment) }
    leafBlock = outer
    return inner
  }

  private fun `val`(ktProperty: KtProperty) {
    val nameRange = ktProperty.nameIdentifier!!.srcRange
    val getter = ktProperty.getter
    val getterBody = getter?.bodyExpression?.text
    val annotationEntries = mutableListOf<KtAnnotationEntry>()
    annotationEntries.addAll(ktProperty.annotationEntries)
    if (getter != null) annotationEntries.addAll(getter.annotationEntries)
    leafBlock.defs.add(DefField(
      nameRange,
      nameRange.text,
      type(ktProperty.typeReference),
      getterBody != null,
      getterBody,
      constructorParam = false,
      suspend = false,
      annotation(annotationEntries, ktProperty),
      type(ktProperty.receiverTypeReference),
      ktProperty.delegateExpression?.srcRange // TODO:: check that working
    ))
  }

  private fun maybePrimaryConstructor(ktClass: KtClassOrObject, iface: KtScope? = null): KtConstructor? {
    val ktPrimaryConstructor = ktClass.primaryConstructor ?: return null

    val outer = leafConstructor
    val constructor = KtConstructor(iface)
    leafConstructor = constructor

    val textRange = ktPrimaryConstructor.textRange
    val src = Src(ktPrimaryConstructor.text) { ktClass.containingFile.text }
    constructor.range = SrcRange(src, textRange.startOffset until textRange.endOffset)

    ktPrimaryConstructor.valueParameters.forEach { valueParameter ->
      constructorVariable(valueParameter)
    }

    leafConstructor = outer
    return constructor
  }

  private fun constructorVariable(ktParameter: KtParameter) {
    val nameRange = ktParameter.nameIdentifier!!.srcRange
    leafConstructor?.defs?.add(DefField(
      nameRange,
      nameRange.text,
      type(ktParameter.typeReference),
      expr = false,
      getterBody = null,
      true,
      suspend = false,
      annotation(ktParameter.annotationEntries, ktParameter)
    ))
  }

  private fun `annotation`(annotationEntries: List<KtAnnotationEntry>, parentElement: PsiElement): KtAnnotations {
    val annotations = KtAnnotations()
    listOf(Open::class.simpleName, Child::class.simpleName, Abstract::class.simpleName, Default::class.simpleName).forEach { annotationName ->
      val annotation = annotationEntries.find { it.shortName?.identifier == annotationName }
      if (annotation != null) {
        val intRange = (annotation.textRange.startOffset + 1) until annotation.textRange.endOffset
        val annotationSrc = Src(annotation.shortName?.identifier!!) { parentElement.containingFile.text }
        annotations.list.add(KtAnnotation(SrcRange(annotationSrc, intRange), emptyList()))
      }
    }
    return annotations
  }

  private fun generatedCodeRegion(comment: PsiComment) {
    if (comment.text.contains("region generated code")) {
      val extensionBlock = leafBlock.parent == null
      val block = if (extensionBlock) leafBlock.children.last() else leafBlock
      if (extensionBlock) {
        block._extensionCode = comment.startOffset..Int.MAX_VALUE
      }
      else {
        block._generatedCode = comment.startOffset..Int.MAX_VALUE
      }
    }
    if (comment.text.contains("endregion")) {
      val extensionBlock = leafBlock.parent == null
      val block = if (extensionBlock) leafBlock.children.last() else leafBlock
      if (extensionBlock && block._extensionCode != null) {
        block._extensionCode = block._extensionCode!!.first..comment.endOffset
      }
      if (!extensionBlock && block._generatedCode != null) {
        block._generatedCode = block._generatedCode!!.first..comment.endOffset
      }
    }
  }

  private fun isInsideGenerateBlock(): Boolean {
    return leafBlock.children.isNotEmpty() && leafBlock.children.last()._extensionCode != null && leafBlock.children.last()._extensionCode!!.last == Int.MAX_VALUE
  }

  private fun range(body: KtClassBody): SrcRange {
    val textRange = body.textRange
    return src.range((textRange.startOffset + 1) until (textRange.endOffset - 1))
  }

  override fun toString(): String = SrcPos(src, pos).toString()

  private val PsiElement.srcRange: SrcRange
    get() {
      val src = Src(text) { containingFile.text }
      return SrcRange(src, textRange.startOffset until textRange.endOffset)
    }
}