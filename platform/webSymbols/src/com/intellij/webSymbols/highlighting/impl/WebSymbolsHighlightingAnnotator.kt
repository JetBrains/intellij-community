// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.highlighting.impl

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
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
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import com.intellij.util.asSafely
import com.intellij.util.containers.MultiMap
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolNameSegment.MatchProblem
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolsBundle
import com.intellij.webSymbols.declarations.WebSymbolDeclarationProvider
import com.intellij.webSymbols.highlighting.WebSymbolHighlightingCustomizer
import com.intellij.webSymbols.highlighting.newSilentAnnotationWithDebugInfo
import com.intellij.webSymbols.impl.WebSymbolNameSegmentImpl
import com.intellij.webSymbols.impl.highlightingEnd
import com.intellij.webSymbols.inspections.impl.WebSymbolsInspectionToolMappingEP
import com.intellij.webSymbols.references.WebSymbolReference
import com.intellij.webSymbols.references.WebSymbolReferenceProblem
import com.intellij.webSymbols.references.WebSymbolReferenceProblem.ProblemKind
import com.intellij.webSymbols.references.impl.IJ_IGNORE_REFS
import com.intellij.webSymbols.references.impl.PsiWebSymbolReferenceProviderImpl
import com.intellij.webSymbols.search.WebSymbolReferenceHints
import com.intellij.webSymbols.utils.applyIfNotNull
import com.intellij.webSymbols.utils.hasOnlyExtensions
import com.intellij.webSymbols.utils.nameSegments
import com.intellij.webSymbols.utils.qualifiedKind
import org.jetbrains.annotations.PropertyKey
import java.util.*

private val INSPECTION_TOOL_INFO_CACHE = Key.create<MutableMap<String, InspectionToolInfo>>("webSymbols.inspectionTools")

class WebSymbolsHighlightingAnnotator : Annotator {

  private val symbolReferencesProvider = PsiWebSymbolReferenceProviderImpl()

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {

    if (element is PsiExternalReferenceHost) {
      // Use service, as WebSymbols may be contributed directly through PsiSymbolReferenceProvider
      PsiSymbolReferenceService.getService().getReferences(element, WebSymbolReference::class.java)
        .filter { it.getProblems().isNotEmpty() }
        .forEach { ref -> annotateReference(ref, holder) }

      // For symbols contributed through PsiWebSymbolReferenceProvider and WebSymbolDeclarationProvider
      // provide automatic symbol kind highlighting
      val multiMap = symbolReferencesProvider.getSymbolOffsetsAndReferences(element, WebSymbolReferenceHints.NO_HINTS).first.copy()

      WebSymbolDeclarationProvider.getAllDeclarations(element, -1).forEach { declaration ->
        multiMap.putValue(declaration.rangeInDeclaringElement.startOffset, declaration.symbol)
      }

      val elementOffset = element.textOffset
      multiMap.entrySet().forEach { (offset, symbols) ->
        highlightSymbols(elementOffset + offset, symbols, element, holder)
      }
    }
  }

  private data class SegmentHighlightingInfo(
    val segment: WebSymbolNameSegment,
    val offset: Int,
    val depth: Int,
    val parentKind: WebSymbolQualifiedKind?,
    val parentTextAttributesKey: TextAttributesKey?,
    val additionalChildSegments: List<Pair<Int, WebSymbolNameSegment>>,
  )

  private fun highlightSymbols(offsetInFile: Int, topLevelSymbols: Collection<WebSymbol>, host: PsiExternalReferenceHost, holder: AnnotationHolder) {
    val result = MultiMap<TextRange, Pair<Int, TextAttributesKey>>()

    val queue = LinkedList(topLevelSymbols.map {
      SegmentHighlightingInfo(WebSymbolNameSegment.create(it), offsetInFile, 0, null,
                              WebSymbolHighlightingCustomizer.getDefaultHostTextAttributes(host), emptyList())
    })
    val processedSymbols = mutableSetOf<WebSymbol>()
    while (queue.isNotEmpty()) {
      val (nameSegment, offset, depth, parentKind, parentTextAttributesKey, additionalChildSegments) = queue.removeFirst()
      val symbols = nameSegment.symbols
      val range = TextRange(nameSegment.start + offset,
                            (nameSegment as WebSymbolNameSegmentImpl).let { it.highlightingEnd ?: it.end } + offset)
      if (symbols.isEmpty()) {
        val segmentKind = nameSegment.symbolKinds.singleOrNull()
        if (nameSegment.problem == MatchProblem.UNKNOWN_SYMBOL && segmentKind != null) {
          WebSymbolHighlightingCustomizer.getTextAttributesFor(segmentKind)
            ?.takeIf { it != parentTextAttributesKey }
            ?.let {
              result.putValue(range, Pair(depth, it))
            }
        }
        continue
      }
      if (symbols.any { it.properties[IJ_IGNORE_REFS] == true }
          || symbols.hasOnlyExtensions()
      ) continue

      if (range.length > 0) {
        val textAttributesKey = symbols.asSequence()
          .filter { !it.extension }
          .mapNotNull { symbol ->
            WebSymbolHighlightingCustomizer.getSymbolTextAttributes(host, symbol, depth)
              ?.let { return@mapNotNull it }

            symbol.properties[WebSymbol.PROP_IJ_TEXT_ATTRIBUTES_KEY]?.asSafely<String>()
              ?.let { TextAttributesKey.find(it) }
              ?.let { return@mapNotNull it }

            if (symbol.qualifiedKind != parentKind)
              WebSymbolHighlightingCustomizer.getTextAttributesFor(symbol.qualifiedKind)
                ?.let { return@mapNotNull it }
            null
          }
          .distinct()
          .singleOrNull()
          ?.takeIf { it != parentTextAttributesKey }

        textAttributesKey?.let {
          result.putValue(range, Pair(depth, it))
        }
        symbols.forEach { s ->
          if (processedSymbols.add(s)) {
            val allNestedSegments = s.nameSegments.map { offset + nameSegment.start to it } + additionalChildSegments
            var i = 0
            while (i < allNestedSegments.size) {
              val (nestedOffset, segment) = allNestedSegments[i++]
              if (segment.start == segment.end) continue
              val segmentNestedSegments = SmartList<Pair<Int, WebSymbolNameSegment>>()
              // Matched sequence patterns are not wrapped with complex patterns, so they appear flattened
              // Expand such patterns using highlightingEnd
              if ((segment.highlightingEnd ?: segment.end) != segment.end && segment.symbols.isNotEmpty()) {
                while (i < allNestedSegments.size) {
                  val (offset2, segment2) = allNestedSegments[i]
                  if (segment2.end + offset2 > segment.highlightingEnd!! + nestedOffset) break
                  segmentNestedSegments.add(offset2 to segment2)
                  i++
                }
              }
              queue.add(SegmentHighlightingInfo(segment, nestedOffset, depth + 1, s.qualifiedKind,
                                                textAttributesKey ?: parentTextAttributesKey, segmentNestedSegments))
            }
          }
        }
      }
    }

    result.entrySet()
      .asSequence()
      .mapNotNull { (range, infos) ->
        val maxDepth = infos.maxOfOrNull { it.first } ?: return@mapNotNull null
        infos.filter { it.first == maxDepth }
          .distinctBy { it.second }
          .singleOrNull()
          ?.let { Pair(range, it.second) }
      }
      .sortedWith(Comparator.comparingInt<Pair<TextRange, TextAttributesKey>> { it.first.startOffset }
                    .thenComparingInt { it.first.length }
      )
      .forEach { (range, textAttributesKey) ->
        holder.newSilentAnnotationWithDebugInfo(HighlightInfoType.SYMBOL_TYPE_SEVERITY, textAttributesKey.externalName)
          .textAttributes(textAttributesKey)
          .needsUpdateOnTyping()
          .range(range)
          .create()
      }
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
        WebSymbolsInspectionToolMappingEP.Companion.get(symbolType.namespace, symbolType.kind, problemKind)?.toolShortName
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