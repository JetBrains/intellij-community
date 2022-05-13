// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package storage.codegen.patcher

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.annotations.Open
import org.jetbrains.deft.codegen.model.*
import org.jetbrains.deft.codegen.model.KtAnnotation
import org.jetbrains.deft.codegen.model.KtConstructor
import org.jetbrains.deft.codegen.model.KtFile
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective
import org.jetbrains.kotlin.psi.psiUtil.isNull
import java.util.*

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
    blockContents()
    println("")
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
        val importedFqName = ktImportDirective.importedFqName?.asString()
        if (importedFqName != null) imports.add(importedFqName)
      }
      importsStart = importList.textRange.startOffset
      importsEnd = importList.textRange.endOffset
    }
    file.setImports(KtImports(importsStart..importsEnd, imports))
  }

  fun blockContents() {
    ktFile.children.filterIsInstance(KtClass::class.java).forEach {ktClass ->
      when {
        ktClass.isEnum() -> {
          `interface`(ktClass, WsEnum)
        }
        ktClass.isData() -> {
          `interface`(ktClass, WsData)
        }
        ktClass.isSealed() -> {
          `interface`(ktClass, WsSealed)
        }
        ktClass.isInterface() -> {
          `interface`(ktClass)
        }
        else -> {

        }
      }
    }
    ktFile.children.filterIsInstance(KtProperty::class.java).forEach { ktProperty -> `val`(ktProperty) }
  }

  fun `interface`(ktClass: KtClass, predefinedInterfaceKind: KtInterfaceKind? = null) {
    val nameRange = ktClass.identifyingElement?.textRange ?: return
    val src = Src(ktClass.name!!) { ktClass.containingFile.text }
    val name = SrcRange(src, nameRange.startOffset until nameRange.endOffset)
    println(ktClass.name)
    //val name = iden()?.name ?: return

    val constructor = maybePrimaryConstructor(ktClass)

    val superTypes = PsiTreeUtil.findChildrenOfType(ktClass, KtSuperTypeEntry::class.java)
    val ktTypes = superTypes.map { KtType(it.srcRange) }
    //val superTypes = maybeTypes()

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
        return KtType(typeElement.srcRange, optional = false, args = ktTypes, annotations = ktAnnotations.list)
      }
      is KtNullableType -> {
        val innerType = typeElement.innerType
        if (innerType == null) return null
        return KtType(innerType.srcRange, optional = true, annotations = ktAnnotations.list)
      }
    }
    return null
  }

  fun maybeBlock(ktClass: KtClass, iface: KtScope? = null): KtBlock {
    val outer = leafBlock
    val properties = ktClass.getProperties()
    if (properties.isEmpty()) {
      // Class has an empty body and the source skips curly braces
      val inner = KtBlock(src, outer, isStub = true, scope = iface)
      //inner.prevElementEnd = src.pos(prevElementEnd)
      outer.children.add(inner)
      return inner
    }

    val inner = KtBlock(src, outer, scope = iface)
    outer.children.add(inner)
    leafBlock = inner
    properties.forEach { ktProperty -> `val`(ktProperty) }
    inner.range = range(ktClass.body!!)
    leafBlock = outer
    return inner
  }

  private fun `val`(ktProperty: KtProperty) {
    val nameRange = ktProperty.nameIdentifier!!.srcRange
    val getterBody = ktProperty.getter?.text
    leafBlock.defs.add(DefField(
      nameRange,
      nameRange.text,
      type(ktProperty.typeReference),
      getterBody != null,
      getterBody,
      constructorParam = false,
      suspend = false,
      annotation(ktProperty.annotationEntries, ktProperty),
      null, //iden.receiver?.let { KtType(it) }, // TODO:: val ModuleEntity.foo: Int
      ktProperty.delegateExpression?.srcRange // TODO:: check that working
    ))
  }

  private fun maybePrimaryConstructor(ktClass: KtClass, iface: KtScope? = null): KtConstructor? {
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
    listOf(Open::class.simpleName, Child::class.simpleName, Abstract::class.simpleName).forEach { annotationName ->
      val annotation = annotationEntries.find { it.shortName?.identifier == annotationName }
      if (annotation != null) {
        val intRange = (annotation.textRange.startOffset + 1) until annotation.textRange.endOffset
        val annotationSrc = Src(annotation.shortName?.identifier!!) { parentElement.containingFile.text }
        annotations.list.add(KtAnnotation(SrcRange(annotationSrc, intRange), emptyList()))
      }
    }
    return annotations
  }

  private fun range(psiElement: PsiElement): SrcRange {
    val textRange = psiElement.textRange
    return src.range(textRange.startOffset..textRange.endOffset)
  }

  override fun toString(): String = SrcPos(src, pos).toString()

  private val PsiElement.srcRange: SrcRange
    get() {
      val src = Src(text) { containingFile.text }
      return SrcRange(src, textRange.startOffset until textRange.endOffset)
    }
}