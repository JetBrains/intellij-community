// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElementDecl
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.ElementsForRename
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.SubjectUsage
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.refactoring.modplan.rename.ModPlanWritableUsageInfo
import com.intellij.refactoring.rename.BindablePsiReference
import com.intellij.refactoring.rename.HeadlessRenameSupportedLanguage
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import com.intellij.refactoring.rename.RenamePsiFileProcessor
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.MultiMap

class ModPlanBridgeRenameProviderTest : BasePlatformTestCase() {
  /** A subclass of the processor of the files: it is another processor, which is not registered on the ModPlan engine. */
  private class SubclassFileProcessor : RenamePsiFileProcessor()

  /** A reference to the file of the same directory that a word of the value names. */
  private open class FileNameReference(element: XmlAttributeValue, range: TextRange) : PsiReferenceBase<XmlAttributeValue>(element, range) {
    override fun resolve(): PsiElement? = element.containingFile.containingDirectory?.findFile(value)
  }

  /** A reference that the legacy rename binds after the write, so the edit on a copy cannot write it. */
  private class BoundFileNameReference(element: XmlAttributeValue, range: TextRange) : FileNameReference(element, range), BindablePsiReference

  /** Gives a [FileNameReference] to each word of the attribute `files`, and a [BoundFileNameReference] to each word of `bound`. */
  class FileNameReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
      registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue("files", "bound"), object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
          val value = element as XmlAttributeValue
          val start = value.valueTextRange.startOffset - value.textRange.startOffset
          val isBound = (value.parent as? XmlAttribute)?.localName == "bound"
          return Regex("\\S+").findAll(value.value).map { word ->
            val range = TextRange(start + word.range.first, start + word.range.last + 1)
            if (isBound) BoundFileNameReference(value, range) else FileNameReference(value, range)
          }.toList().toTypedArray()
        }
      })
    }
  }

  /** The provider of the files with [extraUsagesOf] after the usages that the legacy search finds. */
  private class ExtraUsagesFileProvider(private val extraUsagesOf: (PsiElement) -> List<UsageInfo>)
    : ModPlanBridgeRenameProvider(RenamePsiFileProcessor::class.java) {
    override fun usagesOf(element: PsiElement, newName: String, request: ModPlanRequest, renamed: Map<PsiElement, String>): List<UsageInfo> =
      super.usagesOf(element, newName, request, renamed) + extraUsagesOf(element)
  }

  /** The provider of the files with a conflict of the new name for each element that it analyzes. */
  private class NameConflictFileProvider : ModPlanBridgeRenameProvider(RenamePsiFileProcessor::class.java) {
    override fun nameConflicts(element: PsiElement, newName: String, renamed: Map<PsiElement, String>): MultiMap<PsiElement, String> =
      MultiMap<PsiElement, String>().apply { putValue(element, "name conflict of ${(element as PsiFile).name}") }
  }

  private fun registerFileNameReferences() {
    PsiReferenceContributor.EP_NAME.point.registerExtension(
      PsiReferenceContributorEP().apply {
        language = "XML"
        implementationClass = FileNameReferenceContributor::class.java.name
        pluginDescriptor = DefaultPluginDescriptor("intellij.platform.tests.modPlanBridgeRename")
      },
      testRootDisposable,
    )
  }

  private fun installExtraUsages(extraUsagesOf: (PsiElement) -> List<UsageInfo>) {
    ExtensionTestUtil.maskExtensions(ModPlanRenameProvider.EP_NAME, listOf(ExtraUsagesFileProvider(extraUsagesOf)), testRootDisposable)
  }

  private fun requestOf(
    target: PsiElement,
    newName: String = "b.txt",
    options: ModPlanOptionValues = ModPlanOptionValues.EMPTY,
  ): ModPlanRequest = runReadActionBlocking {
    ModPlanRenameRefactoring.request(project, target, newName, true, options)
  }

  private fun plan(target: PsiElement, newName: String = "b.txt", options: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModPlan {
    val request = requestOf(target, newName, options)
    lastRequest = request
    return inBackground { ModPlanRenameRefactoring.getInstance().plan(request) }
  }

  /** The request of the last [plan]. */
  private var lastRequest: ModPlanRequest? = null

  /** @return the plan of the request of the last [plan] without [excludedUsages], as the flow plans after a preview; call it in [inBackground] */
  private fun planWithout(excludedUsages: List<SmartPsiFileRange>): ModPlan {
    val request = lastRequest!!
    val excluded = runReadActionBlocking { ModPlanRefactoring.excludedUsagesOf(excludedUsages) }
    return ModPlanRenameRefactoring.getInstance()
      .plan(ModPlanChain.withOptionValues(request, request.optionValues().with(ModPlanRefactoring.EXCLUDED_USAGES, excluded)))
  }

  private fun apply(acceptedPlan: ModPlan.AcceptedPlan) {
    assertEquals(ModPlanExecutionResult.Applied, ModPlanExecutor.execute(acceptedPlan.execution, true, acceptedPlan.writeEvents))
  }

  private fun textOf(file: PsiFile): String = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!.text

  fun testFileOfNoReferenceIsRenamedByTheFileProcessor() {
    val file = myFixture.configureByText("a.txt", "a")

    val acceptedPlan = inBackground { ModPlanRenameRefactoring.getInstance().plan(requestOf(file)) } as ModPlan.AcceptedPlan

    assertEquals(ModPlanExecutionResult.Applied, ModPlanExecutor.execute(acceptedPlan.execution, true, acceptedPlan.writeEvents))
    assertEquals("b.txt", file.name)
  }

  fun testSubclassOfARegisteredProcessorIsNotSupported() {
    val file = myFixture.configureByText("a.txt", "a")
    ExtensionTestUtil.maskExtensions(RenamePsiElementProcessorBase.EP_NAME, listOf(SubclassFileProcessor()), testRootDisposable)

    assertFalse(inBackground { ModPlanRenameRefactoring.getInstance().isSupported(requestOf(file)) })
  }

  fun testFileThatTheLegacyRenameGivesToAnotherProcessorIsNotOwnedByTheFileProvider() {
    val file = myFixture.configureByText("a.txt", "a")
    ExtensionTestUtil.maskExtensions(RenamePsiElementProcessorBase.EP_NAME, listOf(SubclassFileProcessor(), RenamePsiFileProcessor()),
                                     testRootDisposable)
    val fileProvider = ModPlanRenameProvider.EP_NAME.findExtensionOrFail(RenamePsiFileProcessor.ModPlanRenamePsiFileProvider::class.java)

    val subject = runReadActionBlocking { SymbolPointer.of(file) }

    assertFalse(inBackground { fileProvider.isOwner(subject) })
    assertFalse(inBackground { ModPlanRenameRefactoring.getInstance().isSupported(requestOf(file)) })
  }

  fun testReferencesInAnotherFileGetTheNewNameAndTwoReferencesInOneElementAreBothWritten() {
    registerFileNameReferences()
    val target = myFixture.addFileToProject("target.txt", "t")
    val one = myFixture.addFileToProject("one.xml", """<root files="target.txt"/>""")
    val two = myFixture.addFileToProject("two.xml", """<root files="target.txt other.txt target.txt"/>""")

    apply(plan(target, newName = "renamed.txt") as ModPlan.AcceptedPlan)

    assertEquals("renamed.txt", target.name)
    assertEquals("""<root files="renamed.txt"/>""", textOf(one))
    assertEquals("""<root files="renamed.txt other.txt renamed.txt"/>""", textOf(two))
  }

  fun testTextOccurrenceIsReplacedOnlyWithTheOption() {
    val target = myFixture.addFileToProject("target.txt", "t")
    val notes = myFixture.addFileToProject("notes.txt", "see target.txt here")

    val withoutOption = plan(target, newName = "renamed.txt") as ModPlan.AcceptedPlan
    val withOption = plan(target, newName = "renamed.txt",
                          options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, true)) as ModPlan.AcceptedPlan

    assertDoesntContain(withoutOption.execution.affectedFiles, notes.virtualFile)
    apply(withOption)
    assertEquals("see renamed.txt here", textOf(notes))
  }

  fun testExcludedTextOccurrenceIsKept() {
    val target = myFixture.addFileToProject("target.txt", "t")
    val kept = myFixture.addFileToProject("kept.txt", "see target.txt here")
    val changed = myFixture.addFileToProject("changed.txt", "see target.txt here")
    val ready = plan(target, newName = "renamed.txt",
                     options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, true)) as ModPlan.AcceptedPlan

    val withoutKept = inBackground { planWithout(ready.usages.filter { it.virtualFile == kept.virtualFile }) } as ModPlan.AcceptedPlan

    apply(withoutKept)
    assertEquals("see target.txt here", textOf(kept))
    assertEquals("see renamed.txt here", textOf(changed))
  }

  fun testReferenceThatTheLegacyRenameBindsIsNotSupported() {
    registerFileNameReferences()
    val target = myFixture.addFileToProject("target.txt", "t")
    myFixture.addFileToProject("usage.xml", """<root bound="target.txt"/>""")

    val refused = plan(target, newName = "renamed.txt") as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refused.reason)
    // The engine gives the location of the subject, not of the reference.
    assertEquals(target.virtualFile, refused.location?.virtualFile)
  }

  fun testUnresolvableCollisionIsABrokenConflictWithItsDescriptionAndNoPlaceOrEdit() {
    val target = myFixture.addFileToProject("target.txt", "t")
    val other = myFixture.addFileToProject("other.txt", "o")
    installExtraUsages { element ->
      listOf(object : UnresolvableCollisionUsageInfo(other, element) {
        override fun getDescription(): String = "collision in other.txt"
      })
    }

    val ready = plan(target, newName = "renamed.txt") as ModPlan.AcceptedPlan

    val conflict = ready.conflicts.conflicts.single()
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
    assertEquals(listOf("collision in other.txt"), conflict.messages)
    assertEmpty(ready.usages)
    assertDoesntContain(ready.execution.affectedFiles, other.virtualFile)
  }

  fun testRenameUsageThatCannotBeWrittenOnCopiesIsABrokenConflict() {
    val target = myFixture.addFileToProject("target.txt", "t")
    val other = myFixture.addFileToProject("other.txt", "o")
    installExtraUsages {
      listOf(object : UsageInfo(other), ModPlanWritableUsageInfo {
        override fun asModPlanEdits(newName: String, renamed: Map<PsiElement, String>): List<ModPlanEdit>? = null
      })
    }

    val ready = plan(target, newName = "renamed.txt") as ModPlan.AcceptedPlan

    val conflict = ready.conflicts.conflicts.single()
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
    assertEquals(listOf(RefactoringBundle.message("rename.usage.unmodifiable")), conflict.messages)
    assertDoesntContain(ready.execution.affectedFiles, other.virtualFile)
  }

  fun testUsageAtARenamedElementHasNoPlace() {
    val target = myFixture.addFileToProject("target.txt", "t")
    val other = myFixture.addFileToProject("other.txt", "o")
    installExtraUsages { element ->
      listOf(UsageInfo(element), object : UsageInfo(other), ModPlanWritableUsageInfo {
        override fun asModPlanEdits(newName: String, renamed: Map<PsiElement, String>): List<ModPlanEdit> =
          emptyList()
      })
    }

    val ready = plan(target, newName = "renamed.txt") as ModPlan.AcceptedPlan

    assertEquals(listOf(other.virtualFile), ready.usages.map { it.virtualFile })
  }

  fun testElementWhoseMetadataWritesItsNameIsNotSupported() {
    HeadlessRenameSupportedLanguage.EP_NAME.point.registerExtension(HeadlessRenameSupportedLanguage().apply { language = "DTD" },
                                                                    testRootDisposable)
    val dtd = myFixture.addFileToProject("a.dtd", "<!ELEMENT foo EMPTY>")
    val declaration = runReadActionBlocking { PsiTreeUtil.findChildOfType(dtd, XmlElementDecl::class.java)!! }

    val refused = plan(declaration, newName = "bar") as ModPlan.RefusedPlan

    assertTrue(inBackground { ModPlanRenameRefactoring.getInstance().isSupported(requestOf(declaration, newName = "bar")) })
    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refused.reason)
    assertEquals(dtd.virtualFile, refused.location?.virtualFile)
  }

  fun testNameConflictOfTheTargetIsInItsAnalysisAndNotInTheAnalysisOfAnotherSubject() {
    val target = myFixture.addFileToProject("target.txt", "t")
    val other = myFixture.addFileToProject("other.txt", "o")
    val provider = NameConflictFileProvider()

    val (analysisOfTarget, analysisOfOther) = inBackground {
      val targetSubject = SymbolPointer.of(target)
      val otherSubject = SymbolPointer.of(other)
      val elementsForRename = ElementsForRename.of(targetSubject, "renamed.txt").plus(ElementsForRename.of(otherSubject, "other2.txt"))
      listOf(provider.analyze(targetSubject, elementsForRename, emptyList()),
             provider.analyze(otherSubject, elementsForRename, emptyList()))
    }

    assertEquals(listOf("name conflict of target.txt"),
                 (analysisOfTarget as ModPlanAnalysisResult.Found).conflicts.conflicts.flatMap { it.messages })
    assertEmpty((analysisOfOther as ModPlanAnalysisResult.Found).conflicts.conflicts)
  }

  fun testUsageThatTheProviderDidNotFindIsNotSupported() {
    val target = myFixture.addFileToProject("target.txt", "t")
    val fileProvider = ModPlanRenameProvider.EP_NAME.findExtensionOrFail(RenamePsiFileProcessor.ModPlanRenamePsiFileProvider::class.java)
    val foreignUsage = object : SubjectUsage {
      override fun place(): SmartPsiFileRange? = null
    }

    val analysis = inBackground {
      val subject = SymbolPointer.of(target)
      fileProvider.analyze(subject, ElementsForRename.of(subject, "renamed.txt"), listOf(foreignUsage))
    }

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, (analysis as ModPlanAnalysisResult.Refused).reason)
  }
}
