// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.toUElement

internal class ThreadingInlayHintsProvider : InlayHintsProvider {

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    if (!DevKitInspectionUtil.isAllowedIncludingTestSources(file)) return null
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(file.project) && !Registry.`is`("devkit.inlay.threading")) return null

    if (JavaPsiFacade.getInstance(file.project).findClass(RequiresEdt::class.java.canonicalName, file.resolveScope) == null) return null

    return ThreadingCollector()
  }

  private class ThreadingCollector : SharedBypassCollector {

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      val uCallExpression = element.toUElement(UCallExpression::class.java) ?: return

      val resolvedMethod = uCallExpression.resolveToUElement()?.asSafely<UMethod>() ?: return

      resolvedMethod.getThreadingStatuses().forEach { threadingStatus ->
        val offset = uCallExpression.methodIdentifier?.sourcePsi?.textRange?.startOffset ?: return@forEach
        sink.addPresentation(InlineInlayPosition(offset, true),
                             tooltip = "@${threadingStatus.getDisplayName()}",
                             hintFormat = HintFormat.default) {
          text("@${threadingStatus.shortName}")
        }
      }

    }
  }
}