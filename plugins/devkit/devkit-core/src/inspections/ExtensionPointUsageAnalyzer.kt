// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.uast.*

/**
 * @author yole
 */

private sealed class EPUsage {
  data class Unknown(val reason: String): EPUsage()
  object Safe : EPUsage()
}

private data class QualifiedCall(val callExpression: UCallExpression, val fullExpression: UExpression)

private fun findQualifiedCall(reference: PsiReference): QualifiedCall? {
  val element = reference.element.toUElement()
  val callExpression = element?.getParentOfType<UCallExpression>()
  if (callExpression != null && element == callExpression.receiver) {
    return QualifiedCall(callExpression, callExpression)
  }

  val qualifiedExpression = element?.getParentOfType<UQualifiedReferenceExpression>()
  if (qualifiedExpression != null && qualifiedExpression.receiver == element) {
    val selector = qualifiedExpression.selector
    if (selector is UCallExpression) {
      return QualifiedCall(selector, qualifiedExpression)
    }
  }

  return null
}

private fun analyzeEPUsage(reference: PsiReference): EPUsage {
  val containingFile = reference.element.containingFile
  val containingFileName = FileUtil.getNameWithoutExtension(containingFile.name)
  if (containingFileName.endsWith("CoreEnvironment") || containingFileName.endsWith("CoreApplicationEnvironment")) {
    // dynamic extension registration in EP is safe
    return EPUsage.Safe
  }
  if (ProjectRootManager.getInstance(containingFile.project).fileIndex.isInTestSourceContent(containingFile.virtualFile)) {
    return EPUsage.Safe
  }

  val qualifiedCall = findQualifiedCall(reference) ?: return EPUsage.Unknown("No call found")
  val methodName = qualifiedCall.callExpression.methodName
  if (methodName == "getExtensions" || methodName == "getExtensionList") {
    return analyzeGetExtensionsCall(qualifiedCall.callExpression, qualifiedCall.fullExpression)
  }

  return EPUsage.Unknown("Unknown call found")
}

private data class SafeCall(val methodQName: String, val paramName: String)

private val safeCalls = listOf(
  SafeCall("com.intellij.util.containers.ContainerUtil.exists", "iterable")
)

private fun analyzeGetExtensionsCall(call: UCallExpression, fullExpression: UExpression): EPUsage {
  val loop = call.getParentOfType<UForEachExpression>()
  if (loop != null) {
    if (fullExpression != loop.iteratedValue) return EPUsage.Unknown("Call is not loop's iterated value")
    val parameter = loop.variable.sourcePsi ?: return EPUsage.Unknown("Can't resolve loop variable")
    for (psiReference in ReferencesSearch.search(parameter).findAll()) {
      val usage = analyzeExtensionInstanceUsage(psiReference)
      if (usage != EPUsage.Safe) return usage
    }
    return EPUsage.Safe
  }

  val enclosingCall = fullExpression.getParentOfType<UCallExpression>()
  if (enclosingCall != null) {
    val callee = enclosingCall.resolve() ?: return EPUsage.Unknown("Unresolved method call")
    val qName = "${callee.containingClass?.qualifiedName}.${callee.name}"
    val param = enclosingCall.getParameterForArgument(fullExpression) ?: return EPUsage.Unknown("Can't find parameter")
    if (safeCalls.any { it.methodQName == qName && it.paramName == param.name }) {
      return EPUsage.Safe
    }
    return EPUsage.Unknown("Extension list passed to unknown method")
  }

  return EPUsage.Unknown("Unknown usage of extension list")
}

private fun analyzeExtensionInstanceUsage(ref: PsiReference): EPUsage {
  val qualifiedCall = findQualifiedCall(ref) ?: run {
    findQualifiedCall(ref)
    return EPUsage.Unknown("Unknown usage of extension instance")
  }
  val returnType = qualifiedCall.callExpression.returnType ?: return EPUsage.Unknown("Unknown call return type")
  if (returnType is PsiPrimitiveType) return EPUsage.Safe
  val returnTypeClass = (returnType as? PsiClassType)?.resolve() ?: return EPUsage.Unknown("Unresolved return type class")
  val project = ref.element.project
  val psiElementClass = JavaPsiFacade.getInstance(project).findClass("com.intellij.psi.PsiElement",
                                                                     GlobalSearchScope.projectScope(project))
                        ?: return EPUsage.Unknown("No PSI element?")
  if (returnTypeClass.isEquivalentTo(psiElementClass) || returnTypeClass.isInheritor(psiElementClass, true)) return EPUsage.Safe
  return EPUsage.Unknown("Unknown return type " + returnTypeClass.qualifiedName)
}

class AnalyzeEPUsageAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val target = file.findElementAt(editor.caretModel.offset)?.getUastParentOfType<UField>() ?: return
    val sourcePsi = target.sourcePsi ?: return
    val fieldType = (target.type as? PsiClassReferenceType)?.rawType() ?: return
    if (fieldType.canonicalText == ExtensionPointName::class.java.name ||
        fieldType.canonicalText == ProjectExtensionPointName::class.java.name) {
      val unsafeUsages = mutableListOf<Pair<PsiReference, EPUsage>>()
      val safeUsages = mutableListOf<PsiReference>()
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        {
          ReferencesSearch.search(sourcePsi).forEach { ref ->
            val usage = runReadAction { analyzeEPUsage(ref) }
            if (usage != EPUsage.Safe) {
              unsafeUsages.add(ref to usage)
            }
            else {
              safeUsages.add(ref)
            }
          }
        }, "Analyzing EP usages", true, file.project
      )
      if (unsafeUsages.isEmpty()) {
        if (safeUsages.isEmpty()) {
          HintManager.getInstance().showErrorHint(editor, "No usages found")
        }
        else {
          HintManager.getInstance().showInformationHint(editor, "All usages are dynamic-safe")
        }
      }
      else {
        val (ref, usage) = unsafeUsages.first()
        val navigationElement = ref.element.navigationElement
        (navigationElement as? Navigatable)?.navigate(true)
        val selectedFileEditor = FileEditorManager.getInstance(file.project).getSelectedEditor(navigationElement.containingFile.virtualFile)
        val selectedTextEditor = (selectedFileEditor as? TextEditor)?.editor
        selectedTextEditor?.let {
          HintManager.getInstance().showInformationHint(it, usage.toString())
        }
      }
    }
    else {
      HintManager.getInstance().showErrorHint(editor, "Not an ExtensionPointName reference")
    }
  }
}
