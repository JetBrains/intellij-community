// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.Action
import org.jetbrains.idea.devkit.dom.ActionOrGroup
import org.jetbrains.idea.devkit.dom.AddToGroup
import org.jetbrains.idea.devkit.dom.Group
import org.jetbrains.idea.devkit.dom.Reference
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.ModuleAnalysis
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService.ModuleKind
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeQodanaInspectionScopeLimiter

internal class BackendActionInFrontendGroupInspection : DevKitPluginXmlInspectionBase() {

  override fun isAllowed(holder: DomElementAnnotationHolder): Boolean {
    val file = holder.fileElement.file
    return super.isAllowed(holder)
           && SplitModeInspectionUtil.isAllowedForSplitModeInspection(file)
           && SplitModeQodanaInspectionScopeLimiter.getInstance(file.project).shouldInspectFileInQodanaMode(file)
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is AddToGroup && element !is Reference) return
    if (element is Reference && element.parent !is Group) return

    if (!isAllowed(holder)) return

    val module = element.module ?: return
    val file = holder.fileElement.file
    val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module, file)

    when (element) {
      is AddToGroup -> checkBackendActionAddedToFrontendGroup(element, moduleAnalysis, holder)
      is Reference -> checkBackendActionReferenceInFrontendGroup(element, element.parent as Group, moduleAnalysis, holder)
    }
  }

  private fun checkBackendActionAddedToFrontendGroup(
    addToGroup: AddToGroup,
    moduleAnalysis: ModuleAnalysis,
    holder: DomElementAnnotationHolder,
  ) {
    val actionRegistration = addToGroup.parent
    val backendActionId = when (actionRegistration) {
      is Action -> {
        if (moduleAnalysis.resolvedModuleKind.kind != ModuleKind.BACKEND) return
        actionRegistration.effectiveId ?: return
      }
      is Reference -> actionRegistration.findReferencedBackendActionId() ?: return
      else -> return
    }

    val frontendGroupId = addToGroup.groupId.stringValue ?: return

    if (!addToGroup.referencesFrontendGroup(frontendGroupId)) return

    reportProblem(
      holder,
      addToGroup.groupId,
      backendActionId,
      frontendGroupId,
    )
  }

  private fun checkBackendActionReferenceInFrontendGroup(
    reference: Reference,
    groupRegistration: Group,
    moduleAnalysis: ModuleAnalysis,
    holder: DomElementAnnotationHolder,
  ) {
    if (moduleAnalysis.resolvedModuleKind.kind != ModuleKind.FRONTEND) return

    val frontendGroupId = groupRegistration.effectiveId ?: return
    val backendActionId = reference.findReferencedBackendActionId() ?: return

    reportProblem(
      holder,
      reference.ref,
      backendActionId,
      frontendGroupId,
    )
  }

  private fun Reference.findReferencedBackendActionId(): String? {
    val actionId = ref.stringValue ?: return null
    var hasBackendActionRegistration = false

    IdeaPluginRegistrationIndex.processAction(
      manager.project,
      actionId,
      GlobalSearchScope.projectScope(manager.project),
    ) { registration ->
      val action = registration as? Action ?: return@processAction true
      if (!action.isRegisteredInModuleKind(ModuleKind.BACKEND)) return@processAction true

      hasBackendActionRegistration = true
      false
    }

    return actionId.takeIf { hasBackendActionRegistration }
  }

  private fun AddToGroup.referencesFrontendGroup(groupId: String): Boolean {
    var hasFrontendGroupRegistration = false

    IdeaPluginRegistrationIndex.processGroup(
      manager.project,
      groupId,
      GlobalSearchScope.projectScope(manager.project),
    ) { registration ->
      val group = registration as? Group ?: return@processGroup true
      if (!group.isRegisteredInModuleKind(ModuleKind.FRONTEND)) return@processGroup true

      hasFrontendGroupRegistration = true
      false
    }

    return hasFrontendGroupRegistration
  }

  private fun reportProblem(
    holder: DomElementAnnotationHolder,
    problemElement: DomElement,
    backendActionId: String,
    frontendGroupId: String,
  ) {
    holder.createProblem(
      problemElement,
      message("inspection.remote.dev.backend.action.in.frontend.group.message", backendActionId, frontendGroupId),
      *SplitModeInspectionExclusionsService.getInstance(problemElement.manager.project).createCommonSuppressionQuickFixes(),
    )
  }

  private fun ActionOrGroup.isRegisteredInModuleKind(expectedKind: ModuleKind): Boolean {
    val module = module ?: return false
    val descriptor = DomUtil.getFile(this)
    return SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module, descriptor).resolvedModuleKind.kind == expectedKind
  }
}
