package com.intellij.webSymbols.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.impl.source.tree.injected.changesHandler.innerRange
import com.intellij.psi.util.startOffset
import com.intellij.webSymbols.WebSymbolsBundle
import com.intellij.webSymbols.inspections.impl.WebSymbolsInspectionToolMappingEP
import com.intellij.webSymbols.references.WebSymbolReference
import com.intellij.webSymbols.references.WebSymbolReferenceProblem
import com.intellij.webSymbols.references.WebSymbolReferenceProblem.ProblemKind
import com.intellij.webSymbols.utils.applyIfNotNull
import org.jetbrains.annotations.PropertyKey

internal class WebSymbolsInspectionsPass(private val psiFile: PsiFile, document: Document) : TextEditorHighlightingPass(psiFile.project, document) {
  override fun doCollectInformation(progress: ProgressIndicator) {
    if (!HighlightingLevelManager.getInstance(myProject).shouldInspect(psiFile)) return
    val referencesWithProblems: MutableList<Pair<Int, WebSymbolReference>> = mutableListOf()
    referencesWithProblems.addAll(collectReferencesFromEntireFile(psiFile) { 0 })

    for (injectedDocument in InjectedLanguageManager.getInstance(psiFile.project).getCachedInjectedDocumentsInRange(psiFile, psiFile.textRange)) {
      if (!injectedDocument.isValid) {
        continue
      }
      InjectedLanguageUtil.enumerate(injectedDocument, psiFile) { injectedFile, places ->
        if (!injectedFile.isValid) return@enumerate
        referencesWithProblems.addAll(collectReferencesFromEntireFile(injectedFile) { offset ->
          val place = places.find { it.innerRange.contains(offset) }
                      ?: return@collectReferencesFromEntireFile null
          val hostOffset = place.rangeInsideHost.startOffset - place.innerRange.startOffset
          hostOffset + (place.host?.startOffset ?: return@collectReferencesFromEntireFile null)
        })
      }
    }

    val myInspectionToolInfos = mutableMapOf<String, InspectionToolInfo>()
    val highlights = referencesWithProblems.flatMap { (offset, ref) -> ref.createProblemAnnotations(offset, myInspectionToolInfos) }
    BackgroundUpdateHighlightersUtil.setHighlightersToEditor(myProject, psiFile, myDocument, 0, psiFile.textLength, highlights, id)
  }

  override fun doApplyInformationToEditor() {
  }

  private fun collectReferencesFromEntireFile(file: PsiFile, offsetProvider: (Int) -> Int?) : List<Pair<Int, WebSymbolReference>> {
    return SyntaxTraverser.psiTraverser(file)
      .flatMap { PsiSymbolReferenceService.getService().getReferences(it, WebSymbolReference::class.java) }
      .filter { it.getProblems().isNotEmpty() }
      .mapNotNull { ref -> offsetProvider(ref.absoluteRange.startOffset)?.let { (Pair(it, ref)) } }
  }

  private val inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).currentProfile

  private fun WebSymbolReference.createProblemAnnotations(offset: Int, map: MutableMap<String, InspectionToolInfo>): List<HighlightInfo> =
    getProblems().mapNotNull { problem ->
      val inspectionInfos = problem.getInspectionInfo(problem.kind, map)
      if (inspectionInfos.isNotEmpty() && inspectionInfos.any { !it.enabled || it.isSuppressedFor(element) })
        return@mapNotNull null

      val firstTool = inspectionInfos.firstOrNull()
      val descriptor = problem.descriptor
      val attributesKey: TextAttributesKey? =
        (descriptor as? ProblemDescriptorBase)?.enforcedTextAttributes
        ?: if (descriptor.highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          firstTool?.let { inspectionProfile.getEditorAttributes(it.shortName, this.element) }
        else null
      val highlightDisplayKey = firstTool?.highlightDisplayKey

      val descriptorFixes = descriptor.fixes
        ?.indices
        ?.map { QuickFixWrapper.wrap(descriptor, it) }
        ?.takeIf { it.isNotEmpty() }

      createHighlightInfo(absoluteRange.shiftRight(offset),
                          ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element, ProblemDescriptorUtil.NONE),
                          firstTool?.shortName,
                          attributesKey,
                          descriptor.highlightType,
                          highlightDisplayKey,
                       inspectionInfos.minOfOrNull { it.severity } ?: problem.kind.defaultSeverity,
                       descriptorFixes ?: highlightDisplayKey
                         ?.let { HighlightDisplayKey.getDisplayNameByKey(it) }
                         ?.let { listOf(EmptyIntentionAction(it)) }
                       ?: emptyList())
    }

  private fun createHighlightInfo(range: TextRange,
                                  @InspectionMessage message: String,
                                  inspectionToolId: String?,
                                  textAttributesKey: TextAttributesKey?,
                                  type: ProblemHighlightType,
                                  displayKey: HighlightDisplayKey?,
                                  severity: HighlightSeverity,
                                  fixesToRegister: List<IntentionAction>): HighlightInfo? {
    val builder = HighlightInfo
      .newHighlightInfo(ProblemDescriptorUtil.getHighlightInfoType(type, severity, SeverityRegistrar.getSeverityRegistrar(myProject)))
      .applyIfNotNull(inspectionToolId) { inspectionToolId(it) }
      .applyIfNotNull(textAttributesKey) { textAttributes(it) }
      .range(range)
      .severity(severity)
      .descriptionAndTooltip(message)
      .group(id)
    for (fix in fixesToRegister) {
      builder.registerFix(fix, null, HighlightDisplayKey.getDisplayNameByKey(displayKey), range, displayKey)
    }
    return builder.create()
  }

  private fun WebSymbolReferenceProblem.getInspectionInfo(problemKind: ProblemKind, map: MutableMap<String, InspectionToolInfo>): List<InspectionToolInfo> =
    symbolKinds
      .mapNotNull { symbolType ->
        WebSymbolsInspectionToolMappingEP.get(symbolType.namespace, symbolType.kind, problemKind)?.toolShortName
      }.map {
        map.computeIfAbsent(it, ::createToolInfo)
      }

  private fun createToolInfo(toolShortName: String): InspectionToolInfo {
    val profile = inspectionProfile

    @Suppress("TestOnlyProblems")
    val tool = profile.getInspectionTool(toolShortName, psiFile)
               ?: run {
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

  private inner class InspectionToolInfo(
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

  companion object {
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
    internal fun ProblemKind.getDefaultProblemMessage(symbolKindName: String?): String {
      @PropertyKey(resourceBundle = WebSymbolsBundle.BUNDLE)
      val key = when (this) {
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
  }

}