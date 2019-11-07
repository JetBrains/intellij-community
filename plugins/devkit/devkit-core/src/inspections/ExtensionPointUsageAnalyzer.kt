// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.hint.HintManager
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.usages.*
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.ExtensionPoints
import org.jetbrains.uast.*
import java.awt.Font
import javax.swing.Icon

/**
 * @author yole
 */

private sealed class EPUsage {
  data class Unknown(val reason: String, val targetElement: PsiElement?): EPUsage()
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
  if (PsiTreeUtil.getNonStrictParentOfType(reference.element, PsiComment::class.java) != null) {
    return EPUsage.Safe
  }

  val qualifiedCall = findQualifiedCall(reference) ?: return EPUsage.Unknown("No call found", reference.element)
  val methodName = qualifiedCall.callExpression.methodName
  if (methodName == "getExtensions" || methodName == "getExtensionList") {
    return analyzeGetExtensionsCall(qualifiedCall.callExpression, qualifiedCall.fullExpression)
  }

  return EPUsage.Unknown("Unknown call found", reference.element)
}

private data class SafeCall(val methodQName: String, val paramName: String)

private val safeCalls = listOf(
  SafeCall("com.intellij.util.containers.ContainerUtil.exists", "iterable")
)

private fun analyzeGetExtensionsCall(call: UCallExpression, fullExpression: UExpression): EPUsage {
  val loop = call.getParentOfType<UForEachExpression>()
  if (loop != null) {
    if (fullExpression != loop.iteratedValue) return EPUsage.Unknown("Call is not loop's iterated value", fullExpression.sourcePsi)
    val parameter = loop.variable.sourcePsi ?: return EPUsage.Unknown("Can't resolve loop variable", loop.sourcePsi)
    for (psiReference in ReferencesSearch.search(parameter).findAll()) {
      val usage = analyzeExtensionInstanceUsage(psiReference)
      if (usage != EPUsage.Safe) return usage
    }
    return EPUsage.Safe
  }

  val enclosingCall = fullExpression.getParentOfType<UCallExpression>()
  if (enclosingCall != null) {
    val callee = enclosingCall.resolve() ?: return EPUsage.Unknown("Unresolved method call", enclosingCall.sourcePsi)
    val qName = "${callee.containingClass?.qualifiedName}.${callee.name}"
    val param = enclosingCall.getParameterForArgument(fullExpression) ?: return EPUsage.Unknown("Can't find parameter", enclosingCall.sourcePsi)
    if (safeCalls.any { it.methodQName == qName && it.paramName == param.name }) {
      return EPUsage.Safe
    }
    return EPUsage.Unknown("Extension list passed to unknown method", enclosingCall.sourcePsi)
  }

  return EPUsage.Unknown("Unknown usage of extension list", call.sourcePsi)
}

private fun analyzeExtensionInstanceUsage(ref: PsiReference): EPUsage {
  val qualifiedCall = findQualifiedCall(ref) ?: run {
    findQualifiedCall(ref)
    return EPUsage.Unknown("Unknown usage of extension instance", ref.element.parent)
  }
  val returnType = qualifiedCall.callExpression.returnType ?: return EPUsage.Unknown("Unknown call return type", qualifiedCall.callExpression.sourcePsi)
  if (returnType is PsiPrimitiveType) {
    if (returnType == PsiPrimitiveType.VOID) return EPUsage.Unknown("Method with void return type", qualifiedCall.callExpression.sourcePsi)  // it may be registering something somewhere
    return EPUsage.Safe
  }
  val returnTypeClass = (returnType as? PsiClassType)?.resolve() ?: return EPUsage.Unknown("Unresolved return type class", qualifiedCall.callExpression.sourcePsi)
  val project = ref.element.project
  val psiElementClass = JavaPsiFacade.getInstance(project).findClass("com.intellij.psi.PsiElement",
                                                                     GlobalSearchScope.projectScope(project))
  if (psiElementClass != null) {
    if (returnTypeClass.isEquivalentTo(psiElementClass) || returnTypeClass.isInheritor(psiElementClass, true)) return EPUsage.Safe
  }

  when (returnTypeClass.qualifiedName) {
    CommonClassNames.JAVA_LANG_STRING, "javax.swing.Icon" -> return EPUsage.Safe
  }
  return EPUsage.Unknown("Unknown return type " + returnTypeClass.qualifiedName, qualifiedCall.callExpression.sourcePsi)
}

class AnalyzeEPUsageAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
    val target = elementAtCaret.getUastParentOfType<UField>()
    if (target != null) {
      analyzeEPFieldUsages(target, file, editor)
    }

    val xmlTag = PsiTreeUtil.getParentOfType(elementAtCaret, XmlTag::class.java)
    if (xmlTag != null) {
      if (xmlTag.name == "extensionPoint") {
        analyzeEPTagUsages(xmlTag, file, editor)
      }
      else if (xmlTag.name == "extensionPoints") {
        batchAnalyzeEPTagUsages(xmlTag, file, editor)
      }
    }
  }

  private fun isEPField(field: PsiField?): Boolean {
    val fieldType = (field?.type as? PsiClassReferenceType)?.rawType() ?: return false
    return fieldType.canonicalText == ExtensionPointName::class.java.name ||
           fieldType.canonicalText == ProjectExtensionPointName::class.java.name
  }

  private fun analyzeEPFieldUsages(target: UField, file: PsiFile, editor: Editor) {
    val sourcePsi = target.sourcePsi ?: return
    if (!isEPField(target.javaPsi as? PsiField)) {
      HintManager.getInstance().showErrorHint(editor, "Not an ExtensionPointName reference")
      return
    }

    val unsafeUsages = mutableListOf<Pair<PsiReference, EPUsage.Unknown>>()
    val safeUsages = mutableListOf<PsiReference>()
    ProgressManager.getInstance().runProcessWithProgressSynchronously({
        processEPFieldUsages(sourcePsi, unsafeUsages, safeUsages)
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
      val usages = unsafeUsages.map { EPElementUsage(it.second.targetElement ?: it.first.element, it.second.reason) }
      showEPElementUsages(file.project, EPUsageTarget(target.sourcePsi as PsiField), usages)
    }
  }

  private fun showEPElementUsages(project: Project, usageTarget: UsageTarget, usages: List<EPElementUsage>) {
    UsageViewManager.getInstance(project).showUsages(arrayOf(usageTarget), usages.toTypedArray(),
                                                          UsageViewPresentation().apply {
                                                            tabText = usageTarget.presentation!!.presentableText
                                                            isOpenInNewTab = true
                                                          })
  }

  private fun processEPFieldUsages(sourcePsi: PsiElement,
                                   unsafeUsages: MutableList<Pair<PsiReference, EPUsage.Unknown>>,
                                   safeUsages: MutableList<PsiReference>) {
    ReferencesSearch.search(sourcePsi).forEach { ref ->
      when (val usage = runReadAction { analyzeEPUsage(ref) }) {
        is EPUsage.Unknown -> unsafeUsages.add(ref to usage)
        is EPUsage.Safe -> safeUsages.add(ref)
      }
    }
  }

  private fun analyzeEPTagUsages(xmlTag: XmlTag, file: PsiFile, editor: Editor) {
    val domElement = DomManager.getDomManager(file.project).getDomElement(xmlTag) as? ExtensionPoint
    if (domElement == null) {
      HintManager.getInstance().showErrorHint(editor, "Not an <extensionPoint>")
      return
    }

    val effectiveClass = domElement.effectiveClass ?: run {
      HintManager.getInstance().showErrorHint(editor, "Can't resolve class for EP")
      return
    }

    val epField = effectiveClass.fields.find { isEPField(it) } ?: run {
      HintManager.getInstance().showErrorHint(editor, "Can't find ExtensionPointName field")
      return
    }

    val epUField = epField.toUElementOfType<UField>() ?: return
    analyzeEPFieldUsages(epUField, file, editor)
  }

  private fun batchAnalyzeEPTagUsages(xmlTag: XmlTag, file: PsiFile, editor: Editor) {
    val domElement = DomManager.getDomManager(file.project).getDomElement(xmlTag) as? ExtensionPoints
    if (domElement == null) {
      HintManager.getInstance().showErrorHint(editor, "Not an <extensionPoints>")
      return
    }

    val safeEPs = mutableListOf<ExtensionPoint>()
    val allUnsafeUsages = mutableListOf<EPElementUsage>()
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        for (extensionPoint in domElement.extensionPoints) {
          runReadAction {
            if (extensionPoint.dynamic.value != null) return@runReadAction
            ProgressManager.getInstance().progressIndicator.text = extensionPoint.effectiveQualifiedName

            val epName = extensionPoint.name.xmlAttributeValue ?: return@runReadAction
            if (!ReferencesSearch.search(epName).anyMatch { isInPluginModule(it.element) }) {
              println("Skipping EP with no extensions in plugins: " + extensionPoint.effectiveQualifiedName)
              return@runReadAction
            }

            val effectiveClass = extensionPoint.effectiveClass ?: return@runReadAction
            val epField = effectiveClass.fields.find { isEPField(it) }
            if (epField == null) {
              allUnsafeUsages.add(EPElementUsage(effectiveClass, "No EP field"))
              return@runReadAction
            }

            val unsafeUsages = mutableListOf<Pair<PsiReference, EPUsage.Unknown>>()
            val safeUsages = mutableListOf<PsiReference>()
            processEPFieldUsages(epField, unsafeUsages, safeUsages)
            if (safeUsages.isNotEmpty() && unsafeUsages.isEmpty()) {
              safeEPs.add(extensionPoint)
            }
            else {
              unsafeUsages.mapTo(allUnsafeUsages) { EPElementUsage(it.second.targetElement ?: it.first.element, it.second.reason) }
            }
          }
        }
      }, "Analyzing extension points", true, file.project)

    showEPElementUsages(file.project, DummyUsageTarget("Safe EPs"), safeEPs.mapNotNull { it.xmlElement }.map { EPElementUsage(it) })
    showEPElementUsages(file.project, DummyUsageTarget("Unsafe EP Usages"), allUnsafeUsages)
  }

  private fun isInPluginModule(element: PsiElement): Boolean {
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return false
    return !module.name.startsWith("intellij.platform") &&
           !module.name.startsWith("intellij.clion") &&
           !module.name.startsWith("intellij.appcode")
  }

  private class EPElementUsage(private val psiElement: PsiElement, private val reason: String = "") : PsiElementUsage {
    override fun getElement() = psiElement

    override fun getPresentation(): UsagePresentation {
      return object : UsagePresentation {
        override fun getTooltipText(): String = ""

        override fun getIcon(): Icon? = null

        override fun getPlainText(): String = psiElement.text

        override fun getText(): Array<TextChunk> = arrayOf(TextChunk(TextAttributes(), psiElement.text),
                                                           TextChunk(TextAttributes().apply { fontType = Font.ITALIC }, reason))
      }
    }

    override fun getLocation(): FileEditorLocation? = null

    override fun canNavigate(): Boolean = psiElement is Navigatable && psiElement.canNavigate()

    override fun canNavigateToSource() = psiElement is Navigatable && psiElement.canNavigateToSource()

    override fun highlightInEditor() {
    }

    override fun selectInEditor() {
    }

    override fun isReadOnly() = false

    override fun navigate(requestFocus: Boolean) {
      (psiElement as? Navigatable)?.navigate(requestFocus)
    }

    override fun isNonCodeUsage(): Boolean = false

    override fun isValid(): Boolean = psiElement.isValid
  }

  private class EPUsageTarget(private val field: PsiField) : UsageTarget {
    override fun getFiles(): Array<VirtualFile>? {
      return field.containingFile?.virtualFile?.let { arrayOf(it) }
    }

    override fun getPresentation(): ItemPresentation? {
      return object : ItemPresentation {
        override fun getLocationString(): String? = null

        override fun getIcon(unused: Boolean): Icon? = field.getIcon(0)

        override fun getPresentableText(): String? {
          return "${field.containingClass?.qualifiedName}.${field.name}"
        }
      }
    }

    override fun canNavigate(): Boolean {
      return (field as? Navigatable)?.canNavigate() ?: false
    }

    override fun getName(): String? {
      return "${field.containingClass?.qualifiedName}.${field.name}"
    }

    override fun findUsages() {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun canNavigateToSource(): Boolean {
      return (field as? Navigatable)?.canNavigateToSource() ?: false
    }

    override fun isReadOnly(): Boolean = false

    override fun navigate(requestFocus: Boolean) {
      (field as? Navigatable)?.navigate(true)
    }

    override fun update() {
    }

    override fun highlightUsages(file: PsiFile, editor: Editor, clearHighlights: Boolean) {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findUsagesInEditor(editor: FileEditor) {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isValid(): Boolean = field.isValid
  }

  private class DummyUsageTarget(val text: String): UsageTarget {
    override fun getFiles(): Array<VirtualFile>? = null

    override fun getPresentation(): ItemPresentation? {
      return object : ItemPresentation {
        override fun getLocationString(): String? = null

        override fun getIcon(unused: Boolean): Icon? = null

        override fun getPresentableText(): String?  = text
      }
    }

    override fun canNavigate(): Boolean = false

    override fun getName(): String? = text

    override fun findUsages() {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun canNavigateToSource() = false

    override fun isReadOnly(): Boolean  = false

    override fun navigate(requestFocus: Boolean) {
    }

    override fun update() {
    }

    override fun highlightUsages(file: PsiFile, editor: Editor, clearHighlights: Boolean) {
    }

    override fun findUsagesInEditor(editor: FileEditor) {
    }

    override fun isValid(): Boolean = true
  }
}
