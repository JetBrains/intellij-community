// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.DebugUtil

fun <R> runReadActionAndWait(action: () -> R): R {
  val application = ApplicationManager.getApplication()
  val result = Ref<R>()
  application.invokeAndWait {
    application.runReadAction {
      result.set(action())
    }
  }
  return result.get()
}

fun PsiElement.findChildByElementType(typeName: String) =
  findChildrenByElementType(typeName).first()

fun PsiElement.findChildrenByElementType(typeName: String): List<PsiElement> {
  val elements = children.filter { it.node.elementType.toString() == typeName }
  if (elements.isEmpty()) {
    throw AssertionError(
      "PsiElement[$typeName] not found in $this\n" +
      "PSI:\n" + DebugUtil.psiToString(this, true) + "\n" +
      "PSI STRUCTURE:\n" + psiStructureToString()
    )
  }
  return elements
}

inline fun <reified T : PsiElement> PsiElement.findChildByType(): T =
  findChildrenByType<T>().first()

inline fun <reified T : PsiElement> PsiElement.findChildrenByType(): List<T> {
  val elements = children.filterIsInstance<T>()
  if (elements.isEmpty()) {
    throw AssertionError(
      "${T::class.java} not found in $this\n" +
      "PSI:\n" + DebugUtil.psiToString(this, true) + "\n" +
      "PSI STRUCTURE:\n" + psiStructureToString()
    )
  }
  return elements
}

fun PsiElement.psiStructureToString(indent: Int = 0): String {
  return buildString {
    append("  ".repeat(indent))
    append(node.elementType.toString())
    append("\n")
    for (child in children) {
      append(child.psiStructureToString(indent + 1))
    }
  }
}
