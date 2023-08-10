// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WebTestUtil")

package com.intellij.webSymbols

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageOptions
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.buildUsageViewQuery
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.impl.referencesAt
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.TestLookupElementPresentation
import com.intellij.usages.Usage
import com.intellij.util.ObjectUtils.coalesce
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.webSymbols.declarations.WebSymbolDeclaration
import com.intellij.webSymbols.declarations.WebSymbolDeclarationProvider
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import junit.framework.TestCase.*
import org.junit.Assert
import java.io.File
import kotlin.math.max
import kotlin.math.min

internal val webSymbolsTestsDataPath get() = "${PlatformTestUtil.getCommunityPath()}/platform/webSymbols/testData/"

fun UsefulTestCase.enableAstLoadingFilter() {
  Registry.get("ast.loading.filter").setValue(true, testRootDisposable)
}

fun UsefulTestCase.enableIdempotenceChecksOnEveryCache() {
  Registry.get("platform.random.idempotence.check.rate").setValue(1, testRootDisposable)
}

fun <T> noAutoComplete(code: () -> T): T {
  val old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
  CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
  try {
    return code()
  }
  finally {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old
  }
}

fun CodeInsightTestFixture.checkNoDocumentationAtCaret() {
  assertNull(renderDocAtCaret())
}

fun CodeInsightTestFixture.checkDocumentationAtCaret() {
  checkDocumentation(renderDocAtCaret())
}

fun CodeInsightTestFixture.checkLookupItems(
  renderPriority: Boolean = false,
  renderTypeText: Boolean = false,
  renderTailText: Boolean = false,
  renderProximity: Boolean = false,
  renderPresentedText: Boolean = false,
  checkDocumentation: Boolean = false,
  containsCheck: Boolean = false,
  locations: List<String> = emptyList(),
  fileName: String = InjectedLanguageManager.getInstance(project).getTopLevelFile(file).virtualFile.nameWithoutExtension,
  expectedDataLocation: String = "",
  lookupItemFilter: (item: LookupElementInfo) -> Boolean = { true },
) {
  val hasDir = expectedDataLocation.isNotEmpty()

  fun checkLookupDocumentation(fileSuffix: String = "") {
    if (!checkDocumentation) return
    val lookupsToCheck = renderLookupItems(false, false, lookupFilter = lookupItemFilter).filter { it.isNotBlank() }
    val lookupElements = lookupElements!!.asSequence().associateBy { it.lookupString }
    for (lookupString in lookupsToCheck) {
      val lookupElement = lookupElements[lookupString]
      assertNotNull("Missing lookup string: $lookupString", lookupElement)
      val doc = IdeDocumentationTargetProvider.getInstance(project)
        .documentationTarget(editor, file, lookupElement!!)
        ?.let { computeDocumentationBlocking(it.createPointer()) }
        ?.html
        ?.trim()

      val sanitizedLookupString = lookupString.replace(Regex("[*\"?<>/\\[\\]:;|,#]"), "_")
      checkDocumentation(doc ?: "<no documentation>", "$fileSuffix#$sanitizedLookupString", expectedDataLocation)
    }
  }

  noAutoComplete {
    if (locations.isEmpty()) {
      completeBasic()
      checkListByFile(
        renderLookupItems(renderPriority, renderTypeText, renderTailText, renderProximity, renderPresentedText, lookupItemFilter),
        expectedDataLocation + (if (hasDir) "/items" else "$fileName.items") + ".txt",
        containsCheck
      )
      checkLookupDocumentation()
    }
    else {
      locations.forEachIndexed { index, location ->
        moveToOffsetBySignature(location)
        completeBasic()
        checkListByFile(
          renderLookupItems(renderPriority, renderTypeText, renderTailText, renderProximity, renderPresentedText, lookupItemFilter),
          expectedDataLocation + (if (hasDir) "/items" else "$fileName.items") + ".${index + 1}.txt",
          containsCheck
        )
        checkLookupDocumentation(".${index + 1}")
      }
    }
  }
}

data class LookupElementInfo(
  val lookupElement: LookupElement,
  val lookupString: String,
  val itemText: String?,
  val tailText: String?,
  val typeText: String?,
  val priority: Double,
  val proximity: Int?,
  val isStrikeout: Boolean,
  val isItemTextBold: Boolean,
  val isItemTextItalic: Boolean,
  val isItemTextUnderline: Boolean,
  val isTypeGreyed: Boolean,
)

private fun CodeInsightTestFixture.checkDocumentation(actualDocumentation: String?,
                                                      fileSuffix: String = ".expected",
                                                      directory: String = "") {
  assertNotNull("No documentation rendered", actualDocumentation)
  val expectedFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file)
                       .virtualFile.nameWithoutExtension + fileSuffix + ".html"
  val path = "$testDataPath/$directory/$expectedFile"
  val file = File(path)
  if (!file.exists()) {
    file.createNewFile()
    Logger.getInstance("DocumentationTest").warn("File $file not found - created!")
  }
  val expectedDocumentation = FileUtil.loadFile(file, "UTF-8", true).trim()
  if (expectedDocumentation != actualDocumentation) {
    throw FileComparisonFailure(expectedFile, expectedDocumentation, actualDocumentation, path)
  }
}

private fun CodeInsightTestFixture.renderDocAtCaret(): String? =
  IdeDocumentationTargetProvider.getInstance(project)
    .documentationTargets(editor, file, caretOffset)
    .mapNotNull { computeDocumentationBlocking(it.createPointer())?.html }
    .also { assertTrue("More then one documentation rendered:\n\n${it.joinToString("\n\n")}", it.size <= 1) }
    .getOrNull(0)
    ?.trim()


@JvmOverloads
fun CodeInsightTestFixture.renderLookupItems(renderPriority: Boolean,
                                             renderTypeText: Boolean,
                                             renderTailText: Boolean = false,
                                             renderProximity: Boolean = false,
                                             renderPresentedText: Boolean = false,
                                             lookupFilter: (item: LookupElementInfo) -> Boolean = { true }): List<String> =
  lookupElements?.asSequence()
    ?.map {
      val presentation = TestLookupElementPresentation.renderReal(it)
      LookupElementInfo(it, it.lookupString, presentation.itemText, presentation.tailText,
                        presentation.typeText, (it as? PrioritizedLookupElement<*>)?.priority?: 0.0,
                        (it as? PrioritizedLookupElement<*>)?.explicitProximity,
                        presentation.isStrikeout, presentation.isItemTextBold,
                        presentation.isItemTextItalic, presentation.isItemTextUnderlined,
                        presentation.isTypeGrayed)
    }
    ?.filter(lookupFilter)
    ?.map { el ->
      val result = StringBuilder()
      if (renderPriority && el.isItemTextBold) {
        result.append('!')
      }
      if (renderPriority && el.isStrikeout) {
        result.append('~')
      }
      result.append(el.lookupString)
      if (renderPresentedText) {
        result.append('[')
        result.append(el.itemText)
        result.append(']')
      }
      if (renderTailText) {
        result.append('%')
        result.append(el.tailText)
      }
      if (renderTypeText) {
        result.append('#')
        result.append(el.typeText)
      }
      if (renderPriority) {
        result.append('#')
          .append(el.priority.toInt())
      }
      if (renderProximity) {
        result.append("+")
          .append(el.proximity ?: 0)
      }
      Pair(el, result.toString())
    }
    ?.sortedWith(
      Comparator.comparing { it: Pair<LookupElementInfo, String> -> -(it.first.priority ?: 0.0) }
        .thenComparingInt { -(it.first.proximity ?: 0) }
        .thenComparing { it: Pair<LookupElementInfo, String> -> it.first.lookupString })
    ?.map { it.second }
    ?.toList()
  ?: emptyList()

fun CodeInsightTestFixture.moveToOffsetBySignature(signature: String) {
  PsiDocumentManager.getInstance(project).commitAllDocuments()
  val offset = file.findOffsetBySignature(signature)
  editor.caretModel.moveToOffset(offset)
}

fun PsiFile.findOffsetBySignature(signature: String): Int {
  var str = signature
  val caretSignature = "<caret>"
  val caretOffset = str.indexOf(caretSignature)
  assert(caretOffset >= 0) { "Caret offset is required" }
  str = str.substring(0, caretOffset) + str.substring(caretOffset + caretSignature.length)
  val pos = text.indexOf(str)
  assertTrue("Failed to locate '$str' in: \n $text", pos >= 0)
  return pos + caretOffset
}

fun CodeInsightTestFixture.webSymbolAtCaret(): WebSymbol? =
  injectionThenHost(file, caretOffset) { file, offset ->
    file.webSymbolDeclarationsAt(offset).takeIf { it.isNotEmpty() }
    ?: if (offset > 0) file.webSymbolDeclarationsAt(offset - 1).takeIf { it.isNotEmpty() } else null
  }
    ?.takeIf { it.isNotEmpty() }
    ?.also { if (it.size > 1) throw AssertionError("Multiple WebSymbolDeclarations found at caret position: $it") }
    ?.firstOrNull()
    ?.symbol
  ?: injectionThenHost(file, caretOffset) { file, offset ->
    file.referencesAt(offset).filter { it.absoluteRange.contains(offset) }.takeIf { it.isNotEmpty() }
    ?: if (offset > 0) file.referencesAt(offset - 1).filter { it.absoluteRange.contains(offset - 1) }.takeIf { it.isNotEmpty() } else null
  }
    ?.also { if (it.size > 1) throw AssertionError("Multiple PsiSymbolReferences found at caret position: $it") }
    ?.resolveToWebSymbols()
    ?.also {
      if (it.size > 1) {
        throw AssertionError("More than one symbol at caret position: $it")
      }
    }
    ?.getOrNull(0)


fun CodeInsightTestFixture.webSymbolSourceAtCaret(): PsiElement? =
  webSymbolAtCaret()?.let { it as? PsiSourcedWebSymbol }?.source

fun CodeInsightTestFixture.resolveWebSymbolReference(signature: String): WebSymbol {
  val symbols = multiResolveWebSymbolReference(signature)
  if (symbols.isEmpty()) {
    throw AssertionError("Reference resolves to null at '$signature'")
  }
  if (symbols.size != 1) {
    throw AssertionError("Reference resolves to more than one element at '" + signature + "': "
                         + symbols)
  }
  return symbols[0]
}

fun CodeInsightTestFixture.multiResolveWebSymbolReference(signature: String): List<WebSymbol> {
  val signatureOffset = file.findOffsetBySignature(signature)
  return injectionThenHost(file, signatureOffset) { file, offset ->
    file.referencesAt(offset)
      .let { refs ->
        if (refs.size > 1) {
          val filtered = refs.filter { it.absoluteRange.contains(signatureOffset) }
          if (filtered.size == 1)
            filtered
          else throw AssertionError("Multiple PsiSymbolReferences found at $signature: $refs")
        }
        else refs
      }
      .resolveToWebSymbols()
      .takeIf { it.isNotEmpty() }
  } ?: emptyList()
}

private fun Collection<PsiSymbolReference>.resolveToWebSymbols(): List<WebSymbol> =
  asSequence()
    .flatMap { it.resolveReference() }
    .filterIsInstance<WebSymbol>()
    .flatMap {
      if (it is WebSymbolMatch
          && it.nameSegments.size == 1
          && it.nameSegments[0].canUnwrapSymbols()) {
        it.nameSegments[0].symbols
      }
      else listOf(it)
    }
    .toList()

private fun PsiFile.webSymbolDeclarationsAt(offset: Int): Collection<WebSymbolDeclaration> {
  for ((element, offsetInElement) in elementsAtOffsetUp(offset)) {
    val declarations = WebSymbolDeclarationProvider.getAllDeclarations(element, offsetInElement)
    if (declarations.isNotEmpty()) {
      return declarations
    }
  }
  return emptyList()
}

private fun <T> injectionThenHost(file: PsiFile, offset: Int, computation: (PsiFile, Int) -> T?): T? {
  val injectedFile = InjectedLanguageManager.getInstance(file.project).findInjectedElementAt(file, offset)?.containingFile
  if (injectedFile != null) {
    PsiDocumentManager.getInstance(file.project).getDocument(injectedFile)
      ?.let { it as? DocumentWindow }
      ?.hostToInjected(offset)
      ?.takeIf { it >= 0 }
      ?.let { computation(injectedFile, it) }
      ?.let { return it }
  }
  return computation(file, offset)
}

fun CodeInsightTestFixture.resolveToWebSymbolSource(signature: String): PsiElement {
  val webSymbol = resolveWebSymbolReference(signature)
  val result = assertInstanceOf<PsiSourcedWebSymbol>(webSymbol).source
  assertNotNull("WebSymbol $webSymbol source is null", result)
  return result!!
}

fun CodeInsightTestFixture.resolveReference(signature: String): PsiElement {
  val offsetBySignature = file.findOffsetBySignature(signature)
  var ref = file.findReferenceAt(offsetBySignature)
  if (ref === null) {
    //possibly an injection
    ref = InjectedLanguageManager.getInstance(project)
      .findInjectedElementAt(file, offsetBySignature)
      ?.findReferenceAt(0)
  }

  assertNotNull("No reference at '$signature'", ref)
  var resolve = ref!!.resolve()
  if (resolve == null && ref is PsiPolyVariantReference) {
    val results = ref.multiResolve(false).filter { it.isValidResult }
    if (results.size > 1) {
      throw AssertionError("Reference resolves to more than one element at '" + signature + "': "
                           + results)
    }
    else if (results.size == 1) {
      resolve = results[0].element
    }

  }
  assertNotNull("Reference resolves to null at '$signature'", resolve)
  return resolve!!
}


fun CodeInsightTestFixture.multiResolveReference(signature: String): List<PsiElement> {
  val offsetBySignature = file.findOffsetBySignature(signature)
  val ref = file.findReferenceAt(offsetBySignature)
  assertNotNull("No reference at '$signature'", ref)
  assertTrue("PsiPolyVariantReference expected", ref is PsiPolyVariantReference)
  val resolveResult = (ref as PsiPolyVariantReference).multiResolve(false)
  assertFalse("Empty reference resolution at '$signature'", resolveResult.isEmpty())
  return resolveResult.mapNotNull { it.element }
}

@JvmOverloads
fun CodeInsightTestFixture.assertUnresolvedReference(signature: String, okWithNoRef: Boolean = false) {
  assertEmpty("Reference at $signature should not resolve to WebSymbols.", multiResolveWebSymbolReference(signature))
  val offsetBySignature = file.findOffsetBySignature(signature)
  val ref = file.findReferenceAt(offsetBySignature)
  if (okWithNoRef && ref == null) {
    return
  }
  assertNotNull("Expected not null reference for signature '$signature' at offset $offsetBySignature in file\n${file.text}", ref)
  val resolved = ref!!.resolve()
  assertNull(
    "Expected that reference for signature '$signature' at offset $offsetBySignature resolves to null but resolved to $resolved (${resolved?.text}) in file ${resolved?.containingFile?.name}",
    resolved)
  if (ref is PsiPolyVariantReference) {
    assertEmpty(ref.multiResolve(false))
  }
}

fun CodeInsightTestFixture.findUsages(target: SearchTarget): MutableCollection<out Usage> {
  val project = project

  val searchScope = coalesce<SearchScope>(target.maximalSearchScope, GlobalSearchScope.allScope(project))
  val allOptions = AllSearchOptions(UsageOptions.createOptions(searchScope), true)
  return buildUsageViewQuery(getProject(), target, allOptions).findAll()
}

@JvmOverloads
@RequiresEdt
fun CodeInsightTestFixture.checkGTDUOutcome(expectedOutcome: GotoDeclarationOrUsageHandler2.GTDUOutcome?, signature: String? = null) {
  if (signature != null) {
    moveToOffsetBySignature(signature)
  }
  val actualSignature = signature ?: editor.currentPositionSignature
  val editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file)
  val offset = editor.caretModel.offset
  val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: file
  val gtduOutcome = GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(editor, file, offset)
  Assert.assertEquals(actualSignature,
                      expectedOutcome,
                      gtduOutcome)
}

fun CodeInsightTestFixture.checkGotoDeclaration(fromSignature: String?, declarationSignature: String, expectedFileName: String? = null) {
  checkGTDUOutcome(GotoDeclarationOrUsageHandler2.GTDUOutcome.GTD, fromSignature)
  val actualSignature = fromSignature ?: editor.currentPositionSignature
  performEditorAction("GotoDeclaration")
  val targetEditor = FileEditorManager.getInstance(project).selectedTextEditor?.topLevelEditor
  if (targetEditor == null) throw NullPointerException(actualSignature)
  val targetFile = PsiDocumentManager.getInstance(project).getPsiFile(targetEditor.document)!!
  if (expectedFileName != null) {
    assertEquals(actualSignature, expectedFileName, PsiDocumentManager.getInstance(project).getPsiFile(targetEditor.document)?.name)
  }
  else {
    assertEquals(actualSignature, targetEditor, editor.topLevelEditor)
  }
  if (!declarationSignature.contains("<caret>") || targetFile.findOffsetBySignature(
      declarationSignature) != targetEditor.caretModel.offset) {
    assertEquals("For go to from: $actualSignature",
                 declarationSignature + if (!declarationSignature.contains("<caret>")) ""
                 else (" [" + file.findOffsetBySignature(declarationSignature) + "]"),
                 targetEditor.currentPositionSignature + "[${targetEditor.caretModel.offset}]")
  }
}

fun CodeInsightTestFixture.checkListByFile(actualList: List<String>, @TestDataFile expectedFile: String, containsCheck: Boolean) {
  val path = "$testDataPath/$expectedFile"
  val file = File(path)
  if (!file.exists() && file.createNewFile()) {
    Logger.getInstance("#WebTestUtilKt").warn("File $file has been created.")
  }
  val actualContents = actualList.joinToString("\n").trim() + "\n"
  val expectedContents = FileUtil.loadFile(file, "UTF-8", true).trim() + "\n"
  if (containsCheck) {
    val expectedList = FileUtil.loadLines(file, "UTF-8").filter { it.isNotBlank() }
    val actualSet = actualList.toSet()
    if (!expectedList.all { actualSet.contains(it) }) {
      throw FileComparisonFailure(expectedFile, expectedContents, actualContents, path)
    }
  }
  else if (expectedContents != actualContents) {
    throw FileComparisonFailure(expectedFile, expectedContents, actualContents, path)
  }
}

fun CodeInsightTestFixture.checkTextByFile(actualContents: String, @TestDataFile expectedFile: String) {
  val path = "$testDataPath/$expectedFile"
  val file = File(path)
  if (!file.exists() && file.createNewFile()) {
    Logger.getInstance("#WebTestUtilKt").warn("File $file has been created.")
  }
  val actualContentsTrimmed = actualContents.trim() + "\n"
  val expectedContents = FileUtil.loadFile(file, "UTF-8", true).trim() + "\n"
  if (expectedContents != actualContentsTrimmed) {
    throw FileComparisonFailure(expectedFile, expectedContents, actualContentsTrimmed, path)
  }
}

fun CodeInsightTestFixture.canRenameWebSymbolAtCaret() =
  webSymbolAtCaret().let {
    it is RenameTarget || it?.renameTarget != null || (it is PsiSourcedWebSymbol && it.source != null)
  }

fun CodeInsightTestFixture.renameWebSymbol(newName: String) {
  val symbol = webSymbolAtCaret() ?: throw AssertionError("No WebSymbol at caret")
  var target: RenameTarget? = null

  for (factory: SymbolRenameTargetFactory in SymbolRenameTargetFactory.EP_NAME.extensions) {
    target = factory.renameTarget(project, symbol)
    if (target != null) break
  }
  if (target == null) {
    target = when (symbol) {
      is RenameTarget -> symbol
      is PsiSourcedWebSymbol -> {
        val psiTarget = symbol.source
                        ?: throw AssertionError("Symbol $symbol provides null source")
        renameElement(psiTarget, newName)
        return
      }
      else -> symbol.renameTarget
              ?: throw AssertionError("Symbol $symbol does not provide rename target nor is a PsiSourcedWebSymbol")
    }
  }
  if (target.createPointer().dereference() == null) {
    throw AssertionError("Target $target pointer dereferences to null")
  }
  renameTarget(target, newName)
}

fun CodeInsightTestFixture.configureAndCopyPaste(sourceFile: String, destinationFile: String) {
  configureFromTempProjectFile(sourceFile)
  performEditorAction(IdeActions.ACTION_EDITOR_COPY)
  configureFromTempProjectFile(destinationFile)
  performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
  WriteAction.runAndWait<Throwable> {
    FileDocumentManager.getInstance().saveAllDocuments()
  }
}

fun doCompletionItemsTest(fixture: CodeInsightTestFixture,
                          fileName: String,
                          goldFileWithExtension: Boolean = false,
                          renderPresentedText: Boolean = false) {
  val fileNameNoExt = FileUtil.getNameWithoutExtension(fileName)
  fixture.configureByFile(fileName)
  WriteAction.runAndWait<Throwable> { WebSymbolsQueryExecutorFactory.getInstance(fixture.project) }

  val document = fixture.getDocument(fixture.file)

  val offsets = mutableListOf<Pair<Int, Boolean>>()

  WriteAction.runAndWait<Throwable> {
    CommandProcessor.getInstance().executeCommand(fixture.project, {
      val chars = document.charsSequence
      var pos: Int
      while (chars.indexOf('|').also { pos = it } >= 0) {
        val strict = chars.length > pos + 1 && chars[pos + 1] == '!'
        offsets.add(Pair(pos, strict))
        if (strict)
          document.deleteString(pos, pos + 2)
        else
          document.deleteString(pos, pos + 1)
      }
    }, null, null)
    PsiDocumentManager.getInstance(fixture.project).commitDocument(document)
  }
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  noAutoComplete {
    offsets.forEachIndexed { index, (offset, strict) ->
      fixture.editor.caretModel.moveToOffset(offset)
      fixture.completeBasic()

      fixture.checkListByFile(
        fixture.renderLookupItems(true, true, true, renderPresentedText = renderPresentedText),
        "gold/${if (goldFileWithExtension) fileName else fileNameNoExt}.${index}.txt", !strict)

      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }
}

private val Editor.currentPositionSignature: String
  get() {
    val caretPos = caretModel.offset
    val text = document.text
    return (text.substring(max(0, caretPos - 15), caretPos) + "<caret>" +
            text.substring(caretPos, min(caretPos + 15, text.length)))
      .replace("\n", "\\n")
  }


private val Editor.topLevelEditor
  get() = if (this is EditorWindow) delegate else this