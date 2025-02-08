// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.webSymbols.WebSymbolsBundle
import com.intellij.webSymbols.inspections.impl.WebSymbolsInspectionToolMappingEP
import com.intellij.webSymbols.references.WebSymbolReference
import com.intellij.webSymbols.references.WebSymbolReferenceProblem
import com.intellij.webSymbols.references.WebSymbolReferenceProblem.ProblemKind
import com.intellij.webSymbols.utils.applyIfNotNull
import org.jetbrains.annotations.PropertyKey

private val INSPECTION_TOOL_INFO_CACHE = Key.create<MutableMap<String, InspectionToolInfo>>("webSymbols.inspectionTools")

class WebSymbolsHighlightingAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    PsiSymbolReferenceService.getService().getReferences(element, WebSymbolReference::class.java)
      .filter { it.getProblems().isNotEmpty() }
      .forEach { ref -> annotateReference(ref, holder) }
  }

  private fun annotateReference(
    reference: WebSymbolReference,
    holder: AnnotationHolder,
  ) {
    val map = holder.currentAnnotationSession
      .getOrCreateUserDataUnsafe(INSPECTION_TOOL_INFO_CACHE) { mutableMapOf() }
    val project = reference.element.project
    val inspectionProfile = InspectionProjectProfileManager.getInstance(project).currentProfile

    reference.getProblems().forEach { problem ->
      val inspectionInfos = problem.getInspectionInfo(problem.kind, map, holder)
      if (inspectionInfos.isNotEmpty() && inspectionInfos.any { !it.enabled || it.isSuppressedFor(reference.element) }) {
        return@forEach
      }

      val firstTool = inspectionInfos.firstOrNull()
      val descriptor = problem.descriptor
      val attributesKey: TextAttributesKey? =
        (descriptor as? ProblemDescriptorBase)?.enforcedTextAttributes
        ?: if (descriptor.highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          firstTool?.let { inspectionProfile.getEditorAttributes(it.shortName, reference.element) }
        else null
      val highlightDisplayKey = firstTool?.highlightDisplayKey

      val descriptorFixes = descriptor.fixes
        ?.indices
        ?.map { QuickFixWrapper.wrap(descriptor, it) }
        ?.takeIf { it.isNotEmpty() }
      val severity = inspectionInfos.minOfOrNull { it.severity } ?: problem.kind.defaultSeverity
      val message = ProblemDescriptorUtil.renderDescriptionMessage(descriptor, reference.element, ProblemDescriptorUtil.NONE)
      holder.newAnnotation(severity, message)
        .range(reference.absoluteRange)
        .highlightType(descriptor.highlightType)
        .tooltip(message)
        .needsUpdateOnTyping()
        .applyIfNotNull(attributesKey) { textAttributes(it) }
        .apply {
          for (fix in descriptorFixes ?: highlightDisplayKey
            ?.let { HighlightDisplayKey.getDisplayNameByKey(it) }
            ?.let { listOf(EmptyIntentionAction(it)) } ?: emptyList()) {
            newFix(fix).range(reference.absoluteRange)
              .applyIfNotNull(highlightDisplayKey) { this.key(it) }
              .registerFix()
          }
        }
        .create()
    }
  }

  private fun WebSymbolReferenceProblem.getInspectionInfo(
    problemKind: ProblemKind, map: MutableMap<String, InspectionToolInfo>, holder: AnnotationHolder,
  ): List<InspectionToolInfo> =
    symbolKinds
      .mapNotNull { symbolType ->
        WebSymbolsInspectionToolMappingEP.get(symbolType.namespace, symbolType.kind, problemKind)?.toolShortName
      }.map {
        map.computeIfAbsent(it) { createToolInfo(it, holder.currentAnnotationSession.file) }
      }

  private fun createToolInfo(toolShortName: String, psiFile: PsiFile): InspectionToolInfo {
    val profile = InspectionProjectProfileManager.getInstance(psiFile.project).currentProfile

    val tool = profile.getInspectionTool(toolShortName, psiFile)
               ?: run {
                 @Suppress("TestOnlyProblems")
                 if (ApplicationManager.getApplication().isUnitTestMode && !InspectionProfileImpl.INIT_INSPECTIONS)
                   return InspectionToolInfo(toolShortName, false, null,
                                             null, HighlightSeverity.WEAK_WARNING)
                 else throw IllegalStateException("Cannot find inspection tool with name $toolShortName")
               }
    val highlightDisplayKey = HighlightDisplayKey.find(toolShortName)
                              ?: throw IllegalStateException("Cannot find HighlightDisplayKey for $toolShortName")
    val errorLevel = profile.getErrorLevel(highlightDisplayKey, psiFile)
    return InspectionToolInfo(toolShortName,
                              profile.isToolEnabled(highlightDisplayKey, psiFile) && errorLevel != HighlightDisplayLevel.DO_NOT_SHOW,
                              highlightDisplayKey, tool, errorLevel.severity)
  }

}

private val ProblemKind.defaultSeverity: HighlightSeverity
  get() =
    when (this) {
      ProblemKind.DeprecatedSymbol -> HighlightSeverity.WEAK_WARNING
      ProblemKind.ObsoleteSymbol -> HighlightSeverity.WARNING
      ProblemKind.UnknownSymbol -> HighlightSeverity.WARNING
      ProblemKind.MissingRequiredPart -> HighlightSeverity.WARNING
      ProblemKind.DuplicatedPart -> HighlightSeverity.WARNING
    }


@InspectionMessage
internal fun getDefaultProblemMessage(kind: ProblemKind, symbolKindName: String?): String {
  @PropertyKey(resourceBundle = WebSymbolsBundle.BUNDLE)
  val key = when (kind) {
    ProblemKind.DeprecatedSymbol -> return WebSymbolsBundle.message("web.inspection.message.deprecated.symbol.message") +
                                           " " +
                                           WebSymbolsBundle.message("web.inspection.message.deprecated.symbol.explanation")
    ProblemKind.ObsoleteSymbol -> return WebSymbolsBundle.message("web.inspection.message.obsolete.symbol.message") +
                                         " " +
                                         WebSymbolsBundle.message("web.inspection.message.deprecated.symbol.explanation")
    ProblemKind.UnknownSymbol -> "web.inspection.message.segment.unrecognized-identifier"
    ProblemKind.MissingRequiredPart -> "web.inspection.message.segment.missing"
    ProblemKind.DuplicatedPart -> "web.inspection.message.segment.duplicated"
  }
  return WebSymbolsBundle.message(key, symbolKindName ?: WebSymbolsBundle.message("web.inspection.message.segment.default-subject"))
}

private class InspectionToolInfo(
  val shortName: String,
  val enabled: Boolean,
  val highlightDisplayKey: HighlightDisplayKey?,
  val toolWrapper: InspectionToolWrapper<*, *>?,
  val severity: HighlightSeverity,
) {
  fun isSuppressedFor(element: PsiElement) =
    InspectionProfileEntry.getSuppressors(element).any { it.isSuppressedFor(element, shortName) }
    || toolWrapper?.tool?.isSuppressedFor(element) == true
}