// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.properties.BundleNameEvaluator
import com.intellij.lang.properties.PropertiesReferenceManager
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
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
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex
import org.jetbrains.idea.devkit.dom.processing.isClassRegistration
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.references.ActionOrGroupIdReference
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class PluginXmlReferencesModuleReachabilityInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (!isAllowed(holder)) return

    when (element) {
      is Extension -> {
        val module = element.module ?: return
        val extensionPoint = element.extensionPoint ?: return
        val epTag = extensionPoint.xmlTag
        val epFqn = extensionPoint.effectiveQualifiedName
        if (!isEpReachableFromModuleByFqn(epFqn, element, module) &&
            !isInSiblingModuleOfSamePlugin(element, epTag, module)) {
          holder.reportUnreachableClassProblem(
            element, epTag, epFqn, module,
            "inspection.plugin.xml.references.module.reachability.extension.point"
          )
        }
      }

      is GenericDomValue<*> -> {
        val module = element.module ?: return
        val value = element.value
        if (value is PsiClass) {
          if (isClassRegistration(element)) return // handled by ComponentModuleRegistrationChecker
          val qualifiedName = value.qualifiedName
          if (!isClassReachableFromModuleByFqn(qualifiedName, element, module)) {
            val referencedClassName = qualifiedName ?: value.name ?: ""
            holder.reportUnreachableClassProblem(
              element, value, referencedClassName, module,
              "inspection.plugin.xml.references.module.reachability.class"
            )
          }
        }

        val referencesElement = (if (element is GenericAttributeValue<*>) element.xmlAttributeValue else element.xmlElement)
          ?: return
        for (ref in referencesElement.references) {
          when (ref) {
            is ActionOrGroupIdReference -> {
              val target = ref.resolve() ?: continue
              if (!isReachableFromModule(element, target, module) &&
                  !isActionOrGroupReachableFromModuleById(ref.canonicalText, element, module) &&
                  !isInSiblingModuleOfSamePlugin(element, target, module)) {
                holder.reportUnreachableClassProblem(
                  element, target, ref.canonicalText, module,
                  "inspection.plugin.xml.references.module.reachability.action.or.group"
                )
              }
            }

            is ResourceBundleReference -> {
              val target = ref.resolve() ?: continue
              if (!isReachableFromModule(element, target, module) &&
                  !isBundleReachableFromModule(ref.canonicalText, element, module)) {
                holder.reportUnreachableClassProblem(
                  element, target, ref.canonicalText, module,
                  "inspection.plugin.xml.references.module.reachability.bundle"
                )
              }
            }
          }
        }
      }
    }
  }

  private fun isEpReachableFromModuleByFqn(fqn: String, element: DomElement, module: Module): Boolean {
    return ExtensionPointIndex.findExtensionPoint(module.project, moduleRuntimeScope(element, module), fqn) != null
  }

  private fun isClassReachableFromModuleByFqn(fqn: String?, element: DomElement, module: Module): Boolean {
    return fqn == null || JavaPsiFacade.getInstance(module.project).findClass(fqn, moduleRuntimeScope(element, module)) != null
  }

  private fun isReachableFromModule(element: DomElement, target: PsiElement, module: Module): Boolean {
    val targetFile = PsiUtilCore.getVirtualFile(target) ?: return true
    return moduleRuntimeScope(element, module).contains(targetFile)
  }

  /**
   * Checked last, and only for names resolved through the plugin's registration scope (extension points, actions, groups):
   * modules of one plugin register into a shared scope, so such names resolve between them without a dependency edge.
   * Classes and resource bundles do not qualify: a non-embedded content module loads them with its own classloader,
   * which sees only declared dependencies. A plugin is built from production roots alone, so the exemption holds only
   * between production roots: a test descriptor lives on the test classpath, where reachability is decided by
   * dependency edges; unmarked content directories are built into nothing.
   */
  private fun isInSiblingModuleOfSamePlugin(element: DomElement, target: PsiElement?, module: Module): Boolean {
    val targetModule = target?.let { ModuleUtilCore.findModuleForPsiElement(it) } ?: return false
    val descriptorFile = DomUtil.getFile(element)
    return isInProductionRoots(PsiUtilCore.getVirtualFile(target), module.project) &&
           isInProductionRoots(descriptorFile.virtualFile, module.project) &&
           areSiblingModulesInSamePlugin(module, targetModule, descriptorFile)
  }

  private fun isActionOrGroupReachableFromModuleById(id: String, element: DomElement, module: Module): Boolean {
    return !IdeaPluginRegistrationIndex.processActionOrGroup(module.project, id, moduleRuntimeScope(element, module)) { false }
  }

  private fun isBundleReachableFromModule(bundleName: String, element: DomElement, module: Module): Boolean {
    return PropertiesReferenceManager.getInstance(module.project)
      .findPropertiesFiles(moduleRuntimeScope(element, module), bundleName, BundleNameEvaluator.DEFAULT)
      .isNotEmpty()
  }

  private fun moduleRuntimeScope(element: DomElement, module: Module): GlobalSearchScope {
    val includeTests = isInTestRoots(DomUtil.getFile(element).virtualFile, module.project)
    return GlobalSearchScope.moduleRuntimeScope(module, includeTests)
  }

  private fun isInTestRoots(file: VirtualFile?, project: Project): Boolean {
    return file != null && ProjectFileIndex.getInstance(project).isInTestSourceContent(file)
  }

  private fun isInProductionRoots(file: VirtualFile?, project: Project): Boolean {
    val fileIndex = ProjectFileIndex.getInstance(project)
    return file != null && fileIndex.isInSourceContent(file) && !fileIndex.isInTestSourceContent(file)
  }

  private fun DomElementAnnotationHolder.reportUnreachableClassProblem(
    element: DomElement,
    referencedElement: PsiElement,
    referencedClassName: String,
    module: Module,
    @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) messageKey: String
  ) {
    val targetModuleName = resolveLocationName(referencedElement)
    this.createProblem(
      element,
      ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
      message(messageKey, referencedClassName, module.name, targetModuleName),
      null,
      *createFixes(element, referencedElement, module)
    )
  }

  private fun resolveLocationName(target: PsiElement): String {
    ModuleUtilCore.findModuleForPsiElement(target)?.let { return " (module '${it.name}')" }
    val targetFile = PsiUtilCore.getVirtualFile(target) ?: return ""
    ProjectFileIndex.getInstance(target.project).findContainingLibraries(targetFile).firstOrNull()
      ?.let { return " (library '${it.name}')" }
    return ""
  }

  /**
   * A test descriptor lives on the test classpath only, which a test dependency already reaches whatever root the target
   * is in; a production descriptor is packaged from production output alone, so only a production target closes its gap.
   * No fix when no scope reaches the target: a module cannot depend on itself, and unmarked content directories are
   * built into nothing.
   */
  private fun createFixes(element: DomElement, target: PsiElement, module: Module): Array<LocalQuickFix> {
    val targetModule = ModuleUtilCore.findModuleForPsiElement(target) ?: return emptyArray()
    if (targetModule == module) return emptyArray()
    val targetFile = PsiUtilCore.getVirtualFile(target)
    val targetInProductionRoots = isInProductionRoots(targetFile, module.project)
    val scope = when {
      isInTestRoots(DomUtil.getFile(element).virtualFile, module.project) &&
      (targetInProductionRoots || isInTestRoots(targetFile, module.project)) -> DependencyScope.TEST
      targetInProductionRoots -> DependencyScope.COMPILE
      else -> return emptyArray()
    }
    // no plugin descriptor covers test output, so a descriptor dependency would describe nothing
    val descriptorDependency = if (targetInProductionRoots) resolveDescriptorDependency(targetModule) else null
    return arrayOf(
      AddModuleDependencyFix(
        targetModuleName = targetModule.name,
        dependencyScope = scope,
        descriptorDependencyId = descriptorDependency?.first,
        isContentModuleDependency = descriptorDependency?.second ?: false,
      )
    )
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
  private val dependencyScope: DependencyScope,
  private val descriptorDependencyId: String?,
  private val isContentModuleDependency: Boolean,
) : LocalQuickFix {

  private val isTestDependency: Boolean get() = dependencyScope == DependencyScope.TEST

  override fun getFamilyName(): String {
    return message("inspection.plugin.xml.references.module.reachability.fix.family.name")
  }

  override fun getName(): String {
    return if (isTestDependency) {
      message("inspection.plugin.xml.references.module.reachability.fix.name.test", targetModuleName)
    } else {
      message("inspection.plugin.xml.references.module.reachability.fix.name", targetModuleName)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val text = when {
      descriptorDependencyId != null && isTestDependency ->
        message("inspection.plugin.xml.references.module.reachability.fix.preview.test.with.descriptor", targetModuleName, descriptorDependencyId)
      descriptorDependencyId != null ->
        message("inspection.plugin.xml.references.module.reachability.fix.preview.with.descriptor", targetModuleName, descriptorDependencyId)
      isTestDependency -> message("inspection.plugin.xml.references.module.reachability.fix.preview.test", targetModuleName)
      else -> message("inspection.plugin.xml.references.module.reachability.fix.preview", targetModuleName)
    }
    return IntentionPreviewInfo.Html(HtmlChunk.text(text))
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val module = ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement) ?: return
    val targetModule = ModuleManager.getInstance(project).findModuleByName(targetModuleName) ?: return
    JavaProjectModelModificationService.getInstance(project)
      .addDependency(module, targetModule, dependencyScope, false)
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
    } else if (ideaPlugin.isV2Descriptor) {
      val dependencies = ideaPlugin.dependencies
      if (dependencies.plugin.none { it.id.stringValue == descriptorDependencyId }) {
        dependencies.addPlugin().id.stringValue = descriptorDependencyId
      }
    } else {
      if (ideaPlugin.depends.none { (it.rawText ?: it.stringValue) == descriptorDependencyId }) {
        ideaPlugin.addDependency().stringValue = descriptorDependencyId
      }
    }
  }
}
