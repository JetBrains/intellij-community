// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil
import com.intellij.lang.LanguageExtension
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.scope.ProjectProductionScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.usages.*
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xml.DomManager
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.ExtensionPoints
import org.jetbrains.idea.devkit.util.ExtensionPointLocator
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.uast.*
import java.awt.Font
import javax.swing.Icon

data class Leak(@Nls val reason: String, val targetElement: PsiElement?)

private data class QualifiedCall(val callExpression: UCallExpression, val fullExpression: UExpression)

private fun findQualifiedCall(element: UElement?): QualifiedCall? {
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

internal class LeakSearchContext(val project: Project, val epName: String?, val ignoreSafeClasses: Boolean) {
  @NonNls
  private val SAFE_CLASSES = setOf(CommonClassNames.JAVA_LANG_STRING, "javax.swing.Icon", "java.net.URL", "java.io.File", "java.net.URI",
                                   "com.intellij.openapi.vfs.pointers.VirtualFilePointer", "com.intellij.openapi.vfs.VirtualFile")
  @NonNls
  private val ANONYMOUS_PASS_THROUGH = mapOf("com.intellij.openapi.util.NotNullLazyValue" to "compute")

  private val searchScope = GlobalSearchScopesCore.filterScope(project, ProjectProductionScope.INSTANCE)
  private val classSafe = mutableMapOf<PsiClass, Boolean>()
  private val processedObjects = mutableSetOf<UExpression>()
  val unsafeUsages = mutableListOf<Pair<PsiReference, Leak>>()
  val safeUsages = mutableListOf<PsiReference>()

  fun processEPFieldUsages(sourcePsi: PsiElement) {
    ReferencesSearch.search(sourcePsi).forEach { ref ->
      val leaks = runReadAction { analyzeEPUsage(ref) }
      if (leaks.isEmpty()) {
        safeUsages.add(ref)
      }
      else {
        for (leak in leaks) {
          unsafeUsages.add(ref to leak)
        }
      }
    }
    WindowManager.getInstance().getStatusBar(project)?.info = ""
  }

  private fun analyzeEPUsage(reference: PsiReference): List<Leak> {
    val containingFile = reference.element.containingFile
    val containingFileName = FileUtil.getNameWithoutExtension(containingFile.name)
    if (containingFileName.endsWith("CoreEnvironment") || containingFileName.endsWith("CoreApplicationEnvironment")) {
      // dynamic extension registration in EP is safe
      return emptyList()
    }
    if (ProjectRootManager.getInstance(containingFile.project).fileIndex.isInTestSourceContent(containingFile.virtualFile)) {
      return emptyList()
    }
    if (PsiTreeUtil.getNonStrictParentOfType(reference.element, PsiComment::class.java) != null) {
      return emptyList()
    }

    val element = reference.element.toUElement()
    val qualifiedCall = findQualifiedCall(element)
    if (qualifiedCall != null) {
      val methodName = qualifiedCall.callExpression.methodName
      if (methodName == "getExtensions" || methodName == "getExtensionList" || methodName == "allForLanguage") {
        return analyzeGetExtensionsCall(qualifiedCall.fullExpression)
      }
      if (methodName == "findExtension" || methodName == "forLanguage") {
        return findObjectLeaks(qualifiedCall.callExpression, DevKitBundle.message("extension.point.analyzer.reason.extension.instance"))
      }
      if (methodName == "getName") {
        return emptyList()
      }
    }

    var parent = element?.uastParent

    while (parent is UQualifiedReferenceExpression && parent.selector == element) {
      parent = parent.uastParent
    }

    if (parent is UQualifiedReferenceExpression) {
      val name = (parent.referenceNameElement as? UIdentifier)?.name
      if (name == "extensions" || name == "extensionList") { // NON-NLS
        return analyzeGetExtensionsCall(parent)
      }
    }

    return listOf(Leak(DevKitBundle.message("extension.point.analyzer.reason.unknown.usage"), reference.element))
  }

  private fun analyzeGetExtensionsCall(fullExpression: UExpression): List<Leak> {
    val loop = fullExpression.getParentOfType<UForEachExpression>()
    if (loop != null) {
      if (fullExpression != loop.iteratedValue) return listOf(
        Leak(DevKitBundle.message("extension.point.analyzer.reason.call.not.loop.value"), fullExpression.sourcePsi))
      return findVariableUsageLeaks(loop.variable, DevKitBundle.message("extension.point.analyzer.reason.extension.instance"))
    }

    return findObjectLeaks(fullExpression, DevKitBundle.message("extension.point.analyzer.reason.extension.list"))
  }

  private fun findEnclosingCall(expr: UElement?): UCallExpression? {
    val call = expr?.getParentOfType<UCallExpression>()
    if (call != null && call.valueArguments.contains(expr)) return call
    val qualifiedExpression = expr?.getParentOfType<UQualifiedReferenceExpression>()
    if (qualifiedExpression != null && qualifiedExpression.receiver == expr) {
      return qualifiedExpression.selector as? UCallExpression
    }
    return null
  }

  private fun findLeaksThroughCall(call: UCallExpression, text: String): List<Leak> {
    val callee = call.resolve() ?: return listOf(Leak(DevKitBundle.message("extension.point.analyzer.reason.unresolved.method.call"), call.sourcePsi))
    if (isSafeCall(callee)) {
      val type = call.returnType
      if (type == null || isSafeType(type)) return emptyList()
      return findObjectLeaks(call, DevKitBundle.message("extension.point.analyzer.reason.return.value",
                                                        text, callee.name, type.presentableText))
    }
    return listOf(Leak(DevKitBundle.message("extension.point.analyzer.reason.impure.method", text), call.sourcePsi))
  }

  private fun isSafeCall(callee: PsiMethod): Boolean {
    if (JavaMethodContractUtil.isPure(callee)) return true
    // Not marked as pure because it's unclear whether side-effects from 'compute' are acceptable
    if (callee.name == "getValue" && callee.containingClass?.qualifiedName == "com.intellij.openapi.util.NotNullLazyValue") return true
    // Not marked as pure because can reorder items for LinkedHashMap, but it's safe here
    if (callee.name == "get" && callee.containingClass?.qualifiedName == CommonClassNames.JAVA_UTIL_MAP) return true
    return false
  }

  private fun isSafeType(type: PsiType?): Boolean {
    if (!ignoreSafeClasses) return false
    return when (type) {
      null -> false
      is PsiPrimitiveType -> true
      is PsiArrayType -> isSafeType(type.deepComponentType)
      is PsiClassType -> {
        val resolveResult = type.resolveGenerics()
        val psiClass = resolveResult.element ?: return false
        var safe = classSafe.putIfAbsent(psiClass, true)
        if (safe == null) {
          safe = isSafeClass(resolveResult)
          classSafe[psiClass] = safe
        }
        safe
      }
      else -> false
    }
  }

  private fun isSafeClass(resolveResult: PsiClassType.ClassResolveResult): Boolean {
    val psiClass = resolveResult.element ?: return false
    val substitutor = resolveResult.substitutor
    return when {
      SAFE_CLASSES.contains(psiClass.qualifiedName) -> true
      psiClass.isEnum -> true
      InheritanceUtil.isInheritor(psiClass, "com.intellij.psi.PsiElement") -> true
      isConcreteExtension(psiClass) -> true
      psiClass.hasModifierProperty(PsiModifier.FINAL) -> {
        psiClass.allFields.any { f -> isSafeType(substitutor.substitute(f.type)) }
      }
      else -> false
    }
  }
  
  private fun isConcreteExtension(psiClass: PsiClass): Boolean {
    if (epName == null) return false
    val extension = ContainerUtil.getOnlyItem(locateExtensionsByPsiClass(psiClass))?.pointer?.element ?: return false
    val extensionTag = DomManager.getDomManager(project).getDomElement(extension) as? Extension
    val extensionPoint = extensionTag?.extensionPoint ?: return false
    if (extensionPoint.effectiveQualifiedName != epName) return false
    val effectiveClass = extensionPoint.effectiveClass
    return effectiveClass != null && psiClass.isInheritor(effectiveClass, true)
  }
  
  private fun findObjectLeaks(e: UElement, @Nls text: String): List<Leak> {
    val targetElement = e.sourcePsi
    if (e is UExpression && targetElement != null) {
      if (!processedObjects.add(e)) {
        return emptyList()
      }
      WindowManager.getInstance().getStatusBar(project)?.info =
        DevKitBundle.message("extension.point.analyzer.analyze.status.bar.info",
                             StringUtil.shortenTextWithEllipsis(targetElement.text, 50, 5),
                             processedObjects.size)
      if (tooManyObjects()) {
        return listOf(Leak(DevKitBundle.message("extension.point.analyzer.reason.too.many.visited.objects"), targetElement))
      }
      val type = e.getExpressionType()
      if (isSafeType(type)) return emptyList()
      val parent = e.uastParent
      if (parent is UParenthesizedExpression || parent is UIfExpression || parent is UBinaryExpressionWithType ||
          parent is UUnaryExpression || (e is UCallExpression && parent is UQualifiedReferenceExpression)) {
        return findObjectLeaks(parent, text)
      }
      if (parent is UPolyadicExpression && parent.operator != UastBinaryOperator.ASSIGN) {
        return emptyList()
      }
      if (parent is UForEachExpression && parent.iteratedValue == e) {
        return findVariableUsageLeaks(parent.variable, DevKitBundle.message("extension.point.analyzer.reason.element.of", text))
      }
      if (parent is USwitchClauseExpression) {
        val switchExpr = parent.getParentOfType<USwitchExpression>()
        if (switchExpr != null) {
          return findObjectLeaks(switchExpr, text)
        }
      }
      val call = findEnclosingCall(e)
      if (call != null) {
        return findLeaksThroughCall(call, text)
      }
      if (parent is UBinaryExpression && parent.operator == UastBinaryOperator.ASSIGN) {
        val leftOperand = parent.leftOperand
        if (leftOperand is USimpleNameReferenceExpression) {
          val target = leftOperand.resolveToUElement()
          if (target is ULocalVariable) {
            return findVariableUsageLeaks(target, text)
          }
        }
      }
      if (parent is ULocalVariable) {
        return findVariableUsageLeaks(parent, text)
      }
      if (parent is UQualifiedReferenceExpression) {
        val target = parent.resolveToUElement()
        if (target is UField) {
          return findObjectLeaks(parent, text)
        }
      }
      if (parent is UReturnExpression) {
        val jumpTarget = parent.jumpTarget
        if (jumpTarget is UMethod) {
          val target = resolveAnonymousClass(jumpTarget)
          if (target != null) {
            return findObjectLeaks(target, text)
          }
          val psiMethod = jumpTarget.javaPsi
          if (psiMethod.name == "getInstance") {
            return listOf(Leak(DevKitBundle.message("extension.point.analyzer.reason.get.instance.method.skipped"), parent.sourcePsi))
          }
          val methodsToFind = mutableSetOf<PsiMethod>()
          methodsToFind.add(psiMethod)
          ContainerUtil.addAll(methodsToFind, *psiMethod.findDeepestSuperMethods())
          
          val result = mutableListOf<Leak>()
          for (methodToFind in methodsToFind) {
            MethodReferencesSearch.search(methodToFind, searchScope, true).forEach(Processor { ref: PsiReference ->
              val element = ref.element
              var uElement = element.toUElement()
              if (uElement is UReferenceExpression) {
                val uParent = element.parent?.toUElement()
                if (uParent is UCallExpression) {
                  uElement = uParent
                } else {
                  val maybeCall = (uParent as? UQualifiedReferenceExpression)?.selector as? UCallExpression
                  if (maybeCall != null) {
                    uElement = maybeCall
                  }
                }
              }
              if (uElement != null) {
                result.addAll(findObjectLeaks(uElement, DevKitBundle.message("extension.point.analyzer.reason.returned.from.method", text, psiMethod.name)))
              }
              !tooManyObjects()
            })
          }
          if (result.isNotEmpty()) {
            result.add(0, Leak(DevKitBundle.message("extension.point.analyzer.reason.leak.returned.from.method", text, psiMethod.name), parent.sourcePsi))
          }
          return result
        }
      }
    }
    return listOf(Leak(DevKitBundle.message("extension.point.analyzer.reason.unknown.usage.text", text), targetElement?.parent))
  }

  private fun resolveAnonymousClass(uMethod: UMethod): UObjectLiteralExpression? {
    val uClass = uMethod.getContainingUClass() ?: return null
    val anonymous = uClass.uastParent as? UObjectLiteralExpression ?: return null
    val onlySuperType = ContainerUtil.getOnlyItem(uClass.uastSuperTypes) ?: return null
    val methodName = ANONYMOUS_PASS_THROUGH[onlySuperType.getQualifiedName()]
    return if (uMethod.name == methodName) anonymous else null
  }

  private fun tooManyObjects() = processedObjects.size > 500

  private fun findVariableUsageLeaks(variable: UVariable, @Nls text: String): List<Leak> {
    val sourcePsi = variable.sourcePsi
    if (sourcePsi == null) {
      return listOf(Leak(DevKitBundle.message("extension.point.analyzer.reason.uast.no.source.psi", text), null))
    }
    val leaks = mutableListOf<Leak>()
    ReferencesSearch.search(sourcePsi, sourcePsi.useScope).forEach(Processor<PsiReference> { psiReference ->
      val ref = psiReference.element.toUElement()
      if (ref != null) {
        leaks.addAll(findObjectLeaks(ref, text))
      }
      !tooManyObjects()
    })
    return leaks
  }
}

class AnalyzeEPUsageAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
    analyze(elementAtCaret, file, editor, false)
  }
}

class AnalyzeEPUsageIgnoreSafeClassesAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
    analyze(elementAtCaret, file, editor, true)
  }
}

private fun analyze(elementAtCaret: PsiElement, file: PsiFile, editor: Editor, ignoreSafeClasses: Boolean) {
  val target = elementAtCaret.getUastParentOfType<UField>()
  if (target != null) {
    val psiClass = (target.javaPsi as? PsiField)?.containingClass
    var epName: String? = null
    if (psiClass != null) {
      epName = ContainerUtil.getOnlyItem(ExtensionPointLocator(psiClass).findDirectCandidates())?.epName
    }
    analyzeEPFieldUsages(target, file, editor, epName, ignoreSafeClasses)
  }

  val xmlTag = PsiTreeUtil.getParentOfType(elementAtCaret, XmlTag::class.java)
  if (xmlTag != null) {
    if (xmlTag.name == "extensionPoint") {
      analyzeEPTagUsages(xmlTag, file, editor, ignoreSafeClasses)
    }
    else if (xmlTag.name == "extensionPoints") {
      batchAnalyzeEPTagUsages(xmlTag, file, editor, ignoreSafeClasses)
    }
  }
}

private fun isEPField(field: PsiField?): Boolean {
  val fieldType = (field?.type as? PsiClassReferenceType)?.rawType() ?: return false
  return fieldType.canonicalText == ExtensionPointName::class.java.name ||
         fieldType.canonicalText == ProjectExtensionPointName::class.java.name ||
         fieldType.canonicalText == LanguageExtension::class.java.name
}

private fun analyzeEPFieldUsages(target: UField, file: PsiFile, editor: Editor, epName: String?, ignoreSafeClasses: Boolean) {
  val sourcePsi = target.sourcePsi ?: return
  if (!isEPField(target.javaPsi as? PsiField)) {
    HintManager.getInstance().showErrorHint(editor, DevKitBundle.message("extension.point.analyzer.not.extension.point.name"))
    return
  }

  val context = LeakSearchContext(file.project, epName, ignoreSafeClasses)
  val task = object : Task.Backgroundable(file.project, DevKitBundle.message("extension.point.analyzer.analyze.title")) {
    override fun run(indicator: ProgressIndicator) {
      context.processEPFieldUsages(sourcePsi)
      ApplicationManager.getApplication().invokeLater(Runnable {
        if (context.unsafeUsages.isEmpty()) {
          if (context.safeUsages.isEmpty()) {
            HintManager.getInstance().showErrorHint(editor, DevKitBundle.message("extension.point.analyzer.analyze.no.usages"))
          }
          else {
            HintManager.getInstance().showInformationHint(editor, DevKitBundle.message("extension.point.analyzer.analyze.usage.all.safe"))
          }
        }
        else {
          val usages = context.unsafeUsages.map { EPElementUsage(it.second.targetElement ?: it.first.element, it.second.reason) }
          showEPElementUsages(file.project, EPUsageTarget(target.sourcePsi as PsiField), usages)
        }
      })
    }
  }
  ProgressManager.getInstance().run(task)
}

private fun showEPElementUsages(project: Project, usageTarget: UsageTarget, usages: List<EPElementUsage>) {
  UsageViewManager.getInstance(project).showUsages(arrayOf(usageTarget), usages.toTypedArray(),
                                                   UsageViewPresentation().apply {
                                                     tabText = usageTarget.presentation!!.presentableText
                                                     isOpenInNewTab = true
                                                   })
}

private fun analyzeEPTagUsages(xmlTag: XmlTag, file: PsiFile, editor: Editor, ignoreSafeClasses: Boolean) {
  val domElement = DomManager.getDomManager(file.project).getDomElement(xmlTag) as? ExtensionPoint
  if (domElement == null) {
    HintManager.getInstance().showErrorHint(editor, DevKitBundle.message("extension.point.analyzer.analyze.xml.not.extension.point"))
    return
  }

  var effectiveClass = domElement.effectiveClass ?: run {
    HintManager.getInstance().showErrorHint(editor, DevKitBundle.message("extension.point.analyzer.analyze.xml.cannot.resolve.ep.class"))
    return
  }

  if (effectiveClass.qualifiedName == "com.intellij.lang.LanguageExtensionPoint") {
    effectiveClass = ContainerUtil.getOnlyItem(domElement.withElements)?.implements?.value ?: run {
      HintManager.getInstance().showErrorHint(editor,
                                              DevKitBundle.message("extension.point.analyzer.analyze.xml.no.implementation.language.extension.point"))
      return
    }
  }

  val epField = effectiveClass.fields.find { isEPField(it) } ?: run {
    HintManager.getInstance().showErrorHint(editor,
                                            DevKitBundle.message("extension.point.analyzer.analyze.xml.no.extension.point.name.field"))
    return
  }

  val epUField = epField.toUElementOfType<UField>() ?: return
  analyzeEPFieldUsages(epUField, file, editor, domElement.effectiveQualifiedName, ignoreSafeClasses)
}

private fun batchAnalyzeEPTagUsages(xmlTag: XmlTag, file: PsiFile, editor: Editor, ignoreSafeClasses: Boolean) {
  val domElement = DomManager.getDomManager(file.project).getDomElement(xmlTag) as? ExtensionPoints
  if (domElement == null) {
    HintManager.getInstance().showErrorHint(editor, DevKitBundle.message("extension.point.analyzer.analyze.xml.batch.not.extension.points"))
    return
  }

  val safeEPs = mutableListOf<ExtensionPoint>()
  val allUnsafeUsages = mutableListOf<EPElementUsage>()
  val task = object : Task.Backgroundable(file.project, DevKitBundle.message("extension.point.analyzer.analyze.xml.batch.title")) {
    override fun run(indicator: ProgressIndicator) {
      val extensionPoints = runReadAction { domElement.extensionPoints }
      for (extensionPoint in extensionPoints) {
        runReadAction {
          if (extensionPoint.dynamic.value != null) return@runReadAction
          indicator.text = extensionPoint.effectiveQualifiedName

          val epName = extensionPoint.name.xmlAttributeValue ?: return@runReadAction
          if (!ReferencesSearch.search(epName).anyMatch { isInPluginModule(it.element) }) {
            println("Skipping EP with no extensions in plugins: " + extensionPoint.effectiveQualifiedName) // NON-NLS
            return@runReadAction
          }

          val effectiveClass = extensionPoint.effectiveClass ?: return@runReadAction
          val epField = effectiveClass.fields.find { isEPField(it) }
          if (epField == null) {
            allUnsafeUsages.add(EPElementUsage(effectiveClass, DevKitBundle.message("extension.point.analyzer.reason.no.ep.field")))
            return@runReadAction
          }

          val context = LeakSearchContext(file.project, extensionPoint.effectiveQualifiedName, ignoreSafeClasses)
          context.processEPFieldUsages(epField)
          if (context.safeUsages.isNotEmpty() && context.unsafeUsages.isEmpty()) {
            safeEPs.add(extensionPoint)
          }
          else {
            context.unsafeUsages.mapTo(allUnsafeUsages) { EPElementUsage(it.second.targetElement ?: it.first.element, it.second.reason) }
          }
        }
      }
      ApplicationManager.getApplication().invokeLater(Runnable {
        showEPElementUsages(file.project, DummyUsageTarget(DevKitBundle.message("extension.point.analyzer.usage.safe.eps")), safeEPs.mapNotNull { it.xmlElement }.map { EPElementUsage(it) })
        showEPElementUsages(file.project, DummyUsageTarget(DevKitBundle.message("extension.point.analyzer.usage.unsafe.eps")), allUnsafeUsages)
      })
    }
  }
  ProgressManager.getInstance().run(task)
}

private fun isInPluginModule(element: PsiElement): Boolean {
  val module = ModuleUtil.findModuleForPsiElement(element) ?: return false
  return !module.name.startsWith("intellij.platform") &&
         !module.name.startsWith("intellij.clion") &&
         !module.name.startsWith("intellij.appcode")
}

private class EPElementUsage(private val psiElement: PsiElement, @Nls private val reason: String = "") : PsiElementUsage {
  override fun getElement() = psiElement

  override fun getPresentation(): UsagePresentation {
    return object : UsagePresentation {
      override fun getTooltipText(): String = ""

      override fun getIcon(): Icon? = null

      override fun getPlainText(): String = psiElement.text

      override fun getText(): Array<TextChunk> = arrayOf(TextChunk(TextAttributes(), psiElement.text.replace(Regex("\\s+"), " ")),
                                                         TextChunk(TextAttributes().apply { fontType = Font.ITALIC },
                                                                   reason))
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

  override fun isValid(): Boolean = field.isValid
}

private class DummyUsageTarget(@Nls val text: String) : UsageTarget {
  override fun getPresentation(): ItemPresentation? {
    return object : ItemPresentation {

      override fun getIcon(unused: Boolean): Icon? = null

      override fun getPresentableText(): String? = text
    }
  }

  override fun canNavigate(): Boolean = false

  override fun getName(): String? = text

  override fun findUsages() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun canNavigateToSource() = false

  override fun isReadOnly(): Boolean = false

  override fun navigate(requestFocus: Boolean) {
  }

  override fun isValid(): Boolean = true
}
