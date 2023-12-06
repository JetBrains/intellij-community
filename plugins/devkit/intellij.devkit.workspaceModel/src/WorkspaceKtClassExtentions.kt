// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.parsing.parseNumericLiteral
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtUserType
import java.util.*

internal fun KtClass.isWorkspaceEntity(): Boolean {
  if (!isInterface()) return false
  val superTypeList = LinkedList<KtSuperTypeListEntry>()
  superTypeList.addAll(superTypeListEntries)
  while (!superTypeList.isEmpty()) {
    val superType = superTypeList.pop()
    val resolvedKtClass = (superType.typeReference?.typeElement as? KtUserType)?.referenceExpression?.mainReference?.resolve() as? KtClass
                          ?: continue
    if (resolvedKtClass.fqName?.asString() == WorkspaceEntity::class.qualifiedName) return true
    resolvedKtClass.superTypeListEntries.forEach { superTypeList.push(it) }
  }
  return false
}

internal fun KtClass.isAbstractEntity(): Boolean {
  val annotationName = Abstract::class.simpleName
  return annotationEntries.any { it.shortName?.identifier == annotationName }
}

internal fun KtClass.getApiVersion(): Int? {
  val annotationName = GeneratedCodeApiVersion::class.simpleName
  val annotation = annotationEntries.find { it.shortName?.identifier == annotationName }
  if (annotation == null) {
    error("$name should contain $annotationName")
  }
  if (annotation.valueArguments.size != 1) {
    error("Annotation $annotationName at $name should contain only one argument")
  }
  val argumentExpression = annotation.valueArguments[0].getArgumentExpression() as? KtConstantExpression
  if (argumentExpression == null) {
    error("Annotation parameter should be int constant")
  }
  val elementType = argumentExpression.node.elementType
  if (elementType == KtNodeTypes.INTEGER_CONSTANT) {
    return parseNumericLiteral(argumentExpression.text, elementType)?.toInt()
  }
  return null
}

internal fun KtClass.getImplVersion(): Int? {
  val annotationName = GeneratedCodeImplVersion::class.simpleName
  val annotation = annotationEntries.find { it.shortName?.identifier == annotationName }
  if (annotation == null) {
    error("$name should contain $annotationName")
  }
  if (annotation.valueArguments.size != 1) {
    error("Annotation $annotationName at $name should contain only one argument")
  }
  val argumentExpression = annotation.valueArguments[0].getArgumentExpression() as? KtConstantExpression
  if (argumentExpression == null) {
    error("Annotation parameter should be int constant")
  }
  val elementType = argumentExpression.node.elementType
  if (elementType == KtNodeTypes.INTEGER_CONSTANT) {
    return parseNumericLiteral(argumentExpression.text, elementType)?.toInt()
  }
  return null
}