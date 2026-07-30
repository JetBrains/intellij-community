// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.properties.ResourceBundleReference
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.JavaProjectModelModificationService
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.processing.isClassRegistration
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.references.ActionOrGroupIdReference
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class PluginXmlReferencesModuleReachabilityInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (!isAllowed(holder)) return
    if (element !is GenericDomValue<*>) return

    val module = element.module ?: return
    val value = element.value
    if (value is PsiClass) {
      if (isClassRegistration(element)) return // handled by ComponentModuleRegistrationChecker
      if (!isReachableFromModule(element, value, module)) {
        val referencedClassName = value.qualifiedName ?: value.name ?: ""
        holder.reportUnreachableClassProblem(
          element, value, referencedClassName, module.name, "inspection.plugin.xml.references.module.reachability.class"
        )
      }
    }

    val referencesElement = (if (element is GenericAttributeValue<*>) element.xmlAttributeValue else element.xmlElement)
      ?: return
    for (ref in referencesElement.references) {
      when (ref) {
        is ActionOrGroupIdReference -> {
          val target = ref.resolve() ?: continue
          if (!isReachableFromModule(element, target, module)) {
            holder.reportUnreachableClassProblem(
              element, target, ref.canonicalText, module.name, "inspection.plugin.xml.references.module.reachability.action.or.group"
            )
          }
        }

        is ResourceBundleReference -> {
          val target = ref.resolve() ?: continue
          if (!isReachableFromModule(element, target, module)) {
            holder.reportUnreachableClassProblem(
              element, target, ref.canonicalText, module.name, "inspection.plugin.xml.references.module.reachability.bundle"
            )
          }
        }
      }
    }
  }

  private fun isReachableFromModule(element: GenericDomValue<*>, target: PsiElement, module: Module): Boolean {
    val targetFile = PsiUtilCore.getVirtualFile(target) ?: return true
    val elementFile = DomUtil.getFile(element).virtualFile
    val includeTests = elementFile != null && ProjectFileIndex.getInstance(module.project).isInTestSourceContent(elementFile)
    return GlobalSearchScope.moduleRuntimeScope(module, includeTests).contains(targetFile)
  }

  private fun DomElementAnnotationHolder.reportUnreachableClassProblem(
    element: DomElement,
    referencedElement: PsiElement,
    referencedClassName: String,
    moduleName: String,
    @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) messageKey: String
  ) {
    val targetModuleName = resolveLocationName(referencedElement)
    this.createProblem(
      element,
      ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
      message(messageKey, referencedClassName, moduleName, targetModuleName),
      null,
      *createFixes(referencedElement)
    )
  }

  private fun resolveLocationName(target: PsiElement): String {
    ModuleUtilCore.findModuleForPsiElement(target)?.let { return " (module '${it.name}')" }
    val targetFile = PsiUtilCore.getVirtualFile(target) ?: return ""
    ProjectFileIndex.getInstance(target.project).findContainingLibraries(targetFile).firstOrNull()
      ?.let { return " (library '${it.name}')" }
    return ""
  }

  private fun createFixes(target: PsiElement): Array<LocalQuickFix> {
    val targetModule = ModuleUtilCore.findModuleForPsiElement(target) ?: return emptyArray()
    val descriptorDependency = resolveDescriptorDependency(targetModule)
    return arrayOf(AddModuleDependencyFix(
      targetModuleName = targetModule.name,
      descriptorDependencyId = descriptorDependency?.first,
      isContentModuleDependency = descriptorDependency?.second ?: false,
    ))
  }

  private fun resolveDescriptorDependency(targetModule: Module): Pair<String, Boolean>? {
    val contentDescriptor = PluginModuleType.getContentModuleDescriptorXml(targetModule)
    if (contentDescriptor != null) {
      val ideaPlugin = DescriptorUtil.getIdeaPlugin(contentDescriptor)
      if (ideaPlugin != null) {
        val moduleName = contentDescriptor.name.removeSuffix(".xml")
        return Pair(moduleName, true)
      }
    }

    val pluginXml = PluginModuleType.getPluginXml(targetModule)
    if (pluginXml != null) {
      val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml)
      if (ideaPlugin != null && ideaPlugin.hasRealPluginId()) {
        return Pair(ideaPlugin.pluginId!!, false)
      }
    }

    return null
  }
}

private class AddModuleDependencyFix(
  private val targetModuleName: String,
  private val descriptorDependencyId: String?,
  private val isContentModuleDependency: Boolean,
) : LocalQuickFix {

  override fun getFamilyName(): String {
    return message("inspection.plugin.xml.references.module.reachability.fix.family.name")
  }

  override fun getName(): String {
    return message("inspection.plugin.xml.references.module.reachability.fix.name", targetModuleName)
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val text = if (descriptorDependencyId != null) {
      message("inspection.plugin.xml.references.module.reachability.fix.preview.with.descriptor", targetModuleName, descriptorDependencyId)
    }
    else {
      message("inspection.plugin.xml.references.module.reachability.fix.preview", targetModuleName)
    }
    return IntentionPreviewInfo.Html(HtmlChunk.text(text))
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val module = ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement) ?: return
    val targetModule = ModuleManager.getInstance(project).findModuleByName(targetModuleName) ?: return
    JavaProjectModelModificationService.getInstance(project)
      .addDependency(module, targetModule, DependencyScope.COMPILE, false)
      .onSuccess {
        ReadAction.nonBlocking<IdeaPlugin?> {
          resolveIdeaPlugin(descriptor)
        }.finishOnUiThread(ModalityState.nonModal()) { ideaPlugin ->
          if (ideaPlugin != null) {
            WriteCommandAction.runWriteCommandAction(project, familyName, null, {
              addDescriptorDependency(ideaPlugin)
            })
          }
        }.submit(AppExecutorUtil.getAppExecutorService())
      }
  }

  private fun resolveIdeaPlugin(descriptor: ProblemDescriptor): IdeaPlugin? {
    if (descriptorDependencyId == null) return null
    val domElement = DomUtil.getDomElement(descriptor.psiElement) ?: return null
    return domElement.getParentOfType(IdeaPlugin::class.java, true)
  }

  private fun addDescriptorDependency(ideaPlugin: IdeaPlugin) {
    if (isContentModuleDependency) {
      val dependencies = ideaPlugin.dependencies
      if (dependencies.moduleEntry.none { it.name.stringValue == descriptorDependencyId }) {
        dependencies.addModuleEntry().name.stringValue = descriptorDependencyId
      }
    }
    else if (ideaPlugin.isV2Descriptor) {
      val dependencies = ideaPlugin.dependencies
      if (dependencies.plugin.none { it.id.stringValue == descriptorDependencyId }) {
        dependencies.addPlugin().id.stringValue = descriptorDependencyId
      }
    }
    else {
      if (ideaPlugin.depends.none { (it.rawText ?: it.stringValue) == descriptorDependencyId }) {
        ideaPlugin.addDependency().stringValue = descriptorDependencyId
      }
    }
  }
}
