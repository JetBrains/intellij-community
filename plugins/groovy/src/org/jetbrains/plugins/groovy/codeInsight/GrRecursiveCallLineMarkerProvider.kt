// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.FunctionUtil
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class GrRecursiveCallLineMarkerProvider : LineMarkerProvider {
  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    return null
  }

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    val lines = mutableSetOf<Int>()
    for (element in elements) {
      ProgressManager.checkCanceled()
      if (element !is GrMethodCall) continue
      val calledMethod = element.resolveMethod()?.asSafely<GrMethod>() ?: continue
      val parentMethod = element.parentOfType<GrMethod>() ?: continue
      if (calledMethod == parentMethod) {
        val invoked = element.invokedExpression
        val leaf = invoked.asSafely<GrReferenceExpression>()?.referenceNameElement ?: continue
        val lineNumber = PsiDocumentManager.getInstance(element.project)?.getDocument(element.containingFile)?.getLineNumber(invoked.endOffset) ?: continue
        if (lines.add(lineNumber)) {
          result.add(LineMarkerInfo(leaf, leaf.textRange, AllIcons.Gutter.RecursiveMethod,
                                    FunctionUtil.constant(JavaBundle.message("line.marker.recursive.call")), null,
                                    GutterIconRenderer.Alignment.RIGHT) { JavaBundle.message("line.marker.recursive.call") })
        }
      }
    }
  }
}