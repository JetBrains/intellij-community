// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Open
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*

class KtScope(val parent: KtScope?, var owner: Any? = null) {
  val ktInterface: KtInterface? get() = owner as? KtInterface

  override fun toString(): String =
    if (parent == null) owner.toString() else "$parent.${owner.toString()}"

  val isRoot get() = parent == null

  // for merging package related files into one scope
  var sharedScope: KtScope? = null
  val parts = mutableListOf<KtScope>()

  val _own = mutableMapOf<String, KtScope>()
  val own: Map<String, KtScope> get() = _own

  // import x.*
  val importedScopes = mutableListOf<KtScope>()

  // import x.y
  val importedNames = mutableMapOf<String, KtPackage>()

  // dependency founded via PSI analyze
  private val psiImportedNames = mutableMapOf<String, KtScope>()

  val file: KtFile?
    get() {
      var i = this
      while (i.owner !is KtFile) i = i.parent ?: return null
      return i.owner as KtFile
    }

  fun def(name: String, value: KtScope) {
    _own[name] = value
    sharedScope?._own?.let { it[name] = value }
  }

  fun resolve(typeName: String): KtScope? {
    var result: KtScope? = this
    typeName.split('.').forEach {
      result = result?.resolveSimpleName(it)
    }

    if (result == null) {
      val ktFile = owner as? KtFile ?: parent?.owner as? KtFile ?: return null
      result = resolveFromPsi(ktFile, typeName)
      if (result != null) {
        when {
          owner is KtFile -> psiImportedNames[typeName] = result!!
          parent?.owner is KtFile -> parent.psiImportedNames[typeName] = result!!
        }
      }
    }
    return result
  }

  private fun resolveSimpleName(typeName: String): KtScope? {
    val names = sharedScope?._own ?: _own
    return names[typeName]
           ?: resolveFromImportedNames(typeName)
           ?: resolveFromImportedScopes(typeName)
           ?: resolveFromPsiImportedNames(typeName)
           ?: parent?.resolve(typeName)
  }

  private fun resolveFromImportedNames(typeName: String) =
    importedNames[typeName]?.scope?.resolve(typeName)

  private fun resolveFromImportedScopes(typeName: String): KtScope? {
    importedScopes.forEach {
      val i = it.resolve(typeName)
      if (i != null) return i
    }

    return null
  }

  private fun resolveFromPsiImportedNames(typeName: String) = psiImportedNames[typeName]

  private fun resolveFromPsi(ktFile: KtFile, typeName: String): KtScope? {
    val module = ktFile.module
    if (module.project == null) return null
    if (typeName == WorkspaceEntity::class.java.simpleName) return null
    val psiFile = PsiManager.getInstance(module.project).findFile(ktFile.virtualFile!!) ?: return null
    if (psiFile !is org.jetbrains.kotlin.psi.KtFile) return null
    var resolvedType: PsiElement? = null
    PsiTreeUtil.processElements(psiFile) { psiElement ->
      if (psiElement is KtTypeReference && psiElement.text == typeName) {
        resolvedType = (psiElement.typeElement as KtUserType).referenceExpression?.mainReference?.resolve()
        return@processElements false
      } else if (psiElement is KtUserType && psiElement.text == typeName) {
        resolvedType = psiElement.referenceExpression?.mainReference?.resolve()
        return@processElements false
      }
      return@processElements true
    }
    val ktClass = resolvedType as? KtClass ?: return null
    val superTypes = ktClass.superTypeListEntries

    // Code for checking supertype FQN
    //((superType.typeReference?.typeElement as? KtUserType)?.referenceExpression?.mainReference?.resolve() as? KtClass)?.fqName?.toString()
    val workspaceEntitySuperType = superTypes.find { superType -> superType.isWorkspaceEntity() }

    val nameRange = ktClass.identifyingElement!!.textRange
    val src = Src(ktClass.name!!) { ktClass.containingFile.text }
    val ktTypes = superTypes.map { KtType(SrcRange(src, it.textRange.startOffset until it.textRange.endOffset)) }

    val annotations = KtAnnotations()
    listOf(Open::class.simpleName, Abstract::class.simpleName).forEach { annotationName ->
      val annotation = ktClass.annotationEntries.find { it.shortName?.identifier == annotationName }
      if (annotation != null) {
        val intRange = (annotation.textRange.startOffset + 1) until annotation.textRange.endOffset
        val annotationSrc = Src(annotation.shortName?.identifier!!) { ktClass.containingFile.text }
        annotations.list.add(KtAnnotation(SrcRange(annotationSrc, intRange), emptyList()))
      }
    }

    val predefinedKind = when {
      ktClass.isData() -> WsData
      ktClass.isEnum() -> WsEnum
      ktClass.isSealed() -> WsSealed
      ktClass.isInterface() && workspaceEntitySuperType != null -> WsPsiEntityInterface
      else -> null
    }
    return KtScope(this, KtInterface(module, this,
                                     SrcRange(src, nameRange.startOffset until nameRange.endOffset),
                                     ktTypes, null, predefinedKind, annotations, true))
  }

  //  val importList = psiFile.importList
  //  val children = importList?.children ?: return null
  //  for (child in children) {
  //    child as KtImportDirective
  //    val resolve = child.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve() ?: return null
  //    val importedFile = resolve.containingFile ?: return null
  //    println("${child.text} ${importedFile.name} ${importedFile.fileType}")
  //    when (importedFile) {
  //      is org.jetbrains.kotlin.psi.KtFile -> {
  //        val `class` = importedFile.classes[0]
  //        //`class`.methods.forEach {
  //        //  println(it.name)
  //        //}
  //        `class`.implementsListTypes.forEach {
  //          println(it.className)
  //        }
  //        println("")
  //      }
  //      is ClsFileImpl -> {
  //        val `class` = importedFile.classes[0]
  //        //`class`.allMethods.forEach {
  //        //  println(it.name)
  //        //}
  //        `class`.implementsListTypes.forEach {
  //          println(it.className)
  //        }
  //        println("")
  //      }
  //      else -> {
  //        error("Unsupported file type")
  //      }
  //    }
  //    println("----------------------------------------")
  //  }
  //}
  //return null

  fun visitTypes(result: MutableList<DefType>) {
    own.values.forEach { inner ->
      inner.ktInterface?.objType?.let {
        result.add(it)
        inner.visitTypes(result)
      }
    }
  }

  fun visitSimpleTypes(result: MutableList<DefType>) {
    own.values.forEach { inner ->
      inner.ktInterface?.simpleType?.let {
        result.add(it)
        inner.visitSimpleTypes(result)
      }
    }
  }

  private fun KtSuperTypeListEntry.isWorkspaceEntity(): Boolean {
    val resolvedType = typeAsUserType?.referenceExpression?.mainReference?.resolve() ?: return false
    val ktClass = resolvedType as? KtClass ?: return false
    if (ktClass.name == WorkspaceEntity::class.java.simpleName) return true
    return ktClass.superTypeListEntries.any { it.isWorkspaceEntity() }
  }
}