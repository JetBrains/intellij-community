// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.codeInspection.options.OptCheckbox
import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.safeDelete.ModPlanLegacySafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ModPlanTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap

class ModPlanLegacySafeDeleteProviderTest : BasePlatformTestCase() {
  private open class BaseDelegate : SafeDeleteProcessorDelegate {
    var isHandled: (PsiElement) -> Boolean = { true }
    var headlessProvider: ModPlanSafeDeleteProvider? = null
    var searchCount = 0
    var findUsages: (PsiElement, MutableList<in UsageInfo>) -> NonCodeUsageSearchInfo? = { _, _ -> null }
    var additionalElements: SafeDeleteProcessorDelegate.AdditionalElementsData = SafeDeleteProcessorDelegate.AdditionalElementsData.NONE
    var additionalElementsFor: ((PsiElement) -> SafeDeleteProcessorDelegate.AdditionalElementsData)? = null
    var conflicts: List<String> = emptyList()
    var preprocess: (List<UsageInfo>) -> List<UsageInfo>? = { it }
    val preparedElements = ArrayList<PsiElement>()

    override fun handlesElement(element: PsiElement?): Boolean = isHandled(element!!)

    override fun asModPlan(): ModPlanSafeDeleteProvider? = headlessProvider

    override fun findUsages(element: PsiElement, allElementsToDelete: Array<out PsiElement>, result: MutableList<in UsageInfo>): NonCodeUsageSearchInfo? {
      searchCount++
      return findUsages(element, result)
    }

    var toSearch: (PsiElement) -> Collection<PsiElement> = { listOf(it) }

    override fun getElementsToSearch(element: PsiElement, allElementsToDelete: Collection<PsiElement>): Collection<PsiElement> = toSearch(element)

    override fun getAdditionalElementsToDelete(element: PsiElement, allElementsToDelete: Collection<PsiElement>): SafeDeleteProcessorDelegate.AdditionalElementsData {
      return additionalElementsFor?.invoke(element) ?: additionalElements
    }

    override fun findConflicts(element: PsiElement, allElementsToDelete: Array<out PsiElement>, usages: Array<out UsageInfo>, conflicts: MultiMap<PsiElement, String>) {
      conflicts.putValues(element, this.conflicts)
    }

    override fun preprocessUsages(project: Project, usages: Array<out UsageInfo>): Array<out UsageInfo>? = preprocess(usages.toList())?.toTypedArray()

    override fun prepareForDeletion(element: PsiElement) {
      preparedElements.add(element)
    }

    override fun isToSearchInComments(element: PsiElement?): Boolean = false

    override fun setToSearchInComments(element: PsiElement?, enabled: Boolean) {
    }

    override fun isToSearchForTextOccurrences(element: PsiElement?): Boolean = false

    override fun setToSearchForTextOccurrences(element: PsiElement?, enabled: Boolean) {
    }
  }

  private class SubclassUsage(element: PsiElement, referencedElement: PsiElement) : SafeDeleteReferenceSimpleDeleteUsageInfo(element, referencedElement, true)

  private val delegate = BaseDelegate()

  private fun install() {
    ExtensionTestUtil.maskExtensions(SafeDeleteProcessorDelegate.EP_NAME, listOf(delegate), testRootDisposable)
    delegate.headlessProvider = ModPlanLegacySafeDeleteProvider(delegate)
  }

  private fun plan(
    target: PsiElement,
    optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY,
    related: Boolean = true,
  ): ModPlan {
    return inBackground {
      val requestOptionValues = if (related) { optionValues } else { optionValues.with(ModPlanSafeDeleteOptions.SEARCH_RELATED, false) }
      ModPlanSafeDeleteRefactoring.getInstance().plan(ModPlanRequest(project, listOf(SymbolPointer.of(target)), requestOptionValues))
    }
  }

  /** @return the questions that a headless run asks, in their order; the step of the plan runs after them */
  private fun questionsOf(target: PsiElement, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY): List<ModPlanQuestion> {
    val askedQuestions = ArrayList<ModPlanQuestion>()
    inBackground {
      val request = ModPlanRequest(project, listOf(SymbolPointer.of(target)), optionValues)
      ModPlanTestUtil.runAnswering(request, ModPlanSafeDeleteRefactoring.getInstance()::chain) { question, currentValues ->
        askedQuestions.add(question)
        currentValues
      }.run(request)
    }
    return askedQuestions
  }

  private fun ready(target: PsiElement, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModPlan.AcceptedPlan =
    plan(target, optionValues) as ModPlan.AcceptedPlan

  private fun refusalOf(target: PsiElement): ModPlan.RefusedPlan = plan(target) as ModPlan.RefusedPlan

  private fun usages(vararg usages: UsageInfo) {
    delegate.findUsages = { _, result -> result.addAll(usages); null }
  }

  fun `test a legacy non-code usage stays a non-code usage`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    usages(SafeDeleteReferenceSimpleDeleteUsageInfo(b, a, 0, 1, true, false))
    install()

    val readyPlan = ready(a)

    val nonCodeUsage = readyPlan.conflicts.nonCodeUsages().single()
    assertEquals(b.virtualFile, nonCodeUsage.location!!.virtualFile)
    val expectedMessage = runReadAction {
      RefactoringBundle.message("non.code.usage.that.is.not.safe.to.delete", RefactoringUIUtil.getDescription(a, true))
    }
    assertEquals(listOf(expectedMessage), nonCodeUsage.messages)
    assertFalse(readyPlan.conflicts.needsConfirmation())
  }

  fun `test unsafe reference of an anonymous class is a broken conflict`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    usages(object : SafeDeleteReferenceUsageInfo(b, a, false) {
      override fun deleteElement() {
      }
    })
    install()

    val readyPlan = ready(a)

    val conflict = readyPlan.conflicts.conflicts.single { it.location?.virtualFile == b.virtualFile }
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
    val expectedMessage = runReadAction {
      RefactoringBundle.message("usage.that.is.not.safe.to.delete", RefactoringUIUtil.getDescription(a, true))
    }
    assertEquals(listOf(expectedMessage), conflict.messages)
  }

  fun `test custom usage that is not editable refuses NOT_SUPPORTED`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    usages(object : SafeDeleteReferenceUsageInfo(b, a, true) {
      override fun deleteElement() {
      }
    })
    install()

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusalOf(a).reason)
  }

  fun `test custom usage that returns no edit is a broken conflict`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    usages(object : SafeDeleteReferenceUsageInfo(b, a, true), ModPlanSafeDeleteUsage {
      override fun deleteElement() {
      }

      override fun asModPlanEdit(): ModPlanEdit? = null
    })
    install()

    val readyPlan = ready(a)

    val conflict = readyPlan.conflicts.conflicts.single { it.location?.virtualFile == b.virtualFile }
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
    val expectedMessage = runReadActionBlocking {
      RefactoringBundle.message("usage.that.is.not.safe.to.delete", RefactoringUIUtil.getDescription(a, true))
    }
    assertEquals(listOf(expectedMessage), conflict.messages)
    assertEquals(listOf(a.virtualFile), deletedFiles(readyPlan))
  }

  fun `test a subclass that keeps the write uses the inherited edit`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    install()

    usages(SafeDeleteReferenceSimpleDeleteUsageInfo(b, a, true))
    val exactPlan = ready(a)
    usages(SubclassUsage(b, a))
    val subclassPlan = ready(a)

    assertTrue(b.virtualFile in deletedFiles(exactPlan))
    assertTrue(b.virtualFile in deletedFiles(subclassPlan))
  }

  fun `test Ask is answered by deleteByDefault`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val c = myFixture.addFileToProject("c.txt", "c")
    delegate.additionalElements = SafeDeleteProcessorDelegate.AdditionalElementsData(listOf(c), SafeDeleteProcessorDelegate.ConfirmationPolicy.Ask("delete c?", false))
    install()

    assertEquals(listOf(a.virtualFile), deletedFiles(ready(a)))
  }

  fun `test Ask of an added element is not read`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val c = myFixture.addFileToProject("c.txt", "c")
    delegate.additionalElementsFor = { element ->
      when (element) {
        a -> SafeDeleteProcessorDelegate.AdditionalElementsData(listOf(b), SafeDeleteProcessorDelegate.ConfirmationPolicy.Ask("ask of a", true))
        b -> SafeDeleteProcessorDelegate.AdditionalElementsData(listOf(c), SafeDeleteProcessorDelegate.ConfirmationPolicy.Ask("ask of b", true))
        else -> SafeDeleteProcessorDelegate.AdditionalElementsData.NONE
      }
    }
    install()

    assertEquals(setOf(a.virtualFile, b.virtualFile), deletedFiles(ready(a)).toSet())
  }

  fun `test the elements to search and the usages are searched once for a run`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    var elementsToSearchCount = 0
    delegate.toSearch = { elementsToSearchCount++; listOf(it) }
    install()

    questionsOf(a)

    assertEquals(1, elementsToSearchCount)
    assertEquals(1, delegate.searchCount)
  }

  fun `test only the owner preprocesses the usages`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val otherDelegate = BaseDelegate().apply {
      isHandled = { false }
      preprocess = { throw AssertionError("The preprocessing of a delegate that does not own the element may show a dialog") }
    }
    usages(SafeDeleteReferenceSimpleDeleteUsageInfo(b, a, true))
    ExtensionTestUtil.maskExtensions(SafeDeleteProcessorDelegate.EP_NAME, listOf(delegate, otherDelegate), testRootDisposable)
    delegate.headlessProvider = ModPlanLegacySafeDeleteProvider(delegate)

    val readyPlan = ready(a)

    assertEquals(setOf(a.virtualFile, b.virtualFile), deletedFiles(readyPlan).toSet())
    assertTrue(readyPlan.conflicts.conflicts.isEmpty())
  }

  fun `test related elements are not searched without related`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    delegate.toSearch = { listOf(it, b) }
    install()

    assertEquals(setOf(a.virtualFile, b.virtualFile), deletedFiles(ready(a)).toSet())
    assertEquals(listOf(a.virtualFile), deletedFiles(plan(a, related = false) as ModPlan.AcceptedPlan))
  }

  fun `test Ask is still read without related`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val c = myFixture.addFileToProject("c.txt", "c")
    delegate.additionalElements = SafeDeleteProcessorDelegate.AdditionalElementsData(listOf(c), SafeDeleteProcessorDelegate.ConfirmationPolicy.Ask("delete c?", true))
    install()

    val withC = plan(a, related = false) as ModPlan.AcceptedPlan

    assertEquals(setOf(a.virtualFile, c.virtualFile), deletedFiles(withC).toSet())
  }

  fun `test Ask without an option is no question`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val c = myFixture.addFileToProject("c.txt", "c")
    delegate.additionalElements = SafeDeleteProcessorDelegate.AdditionalElementsData(listOf(c), SafeDeleteProcessorDelegate.ConfirmationPolicy.Ask("delete c?", false))
    install()

    val askedControls = questionsOf(a).filterIsInstance<ModPlanQuestion.Form>().flatMap { it.pane().allControls() }
    assertTrue(askedControls.filterIsInstance<OptCheckbox>().none { it.description() == HtmlChunk.text("delete c?") })
  }

  fun `test additional elements without a question are deleted`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val c = myFixture.addFileToProject("c.txt", "c")
    delegate.additionalElements = SafeDeleteProcessorDelegate.AdditionalElementsData(listOf(c))
    install()

    assertEquals(setOf(a.virtualFile, c.virtualFile), deletedFiles(ready(a)).toSet())
  }

  fun `test the first legacy delegate owns the element`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val shadowDelegate = BaseDelegate()
    ExtensionTestUtil.maskExtensions(SafeDeleteProcessorDelegate.EP_NAME, listOf(shadowDelegate, delegate), testRootDisposable)
    delegate.headlessProvider = ModPlanLegacySafeDeleteProvider(delegate)

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusalOf(a).reason)
    shadowDelegate.isHandled = { false }
    assertEquals(listOf(a.virtualFile), deletedFiles(ready(a)))
  }

  fun `test a native provider wins over the bridge`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install()
    val nativeProvider = object : ModPlanSafeDeleteProvider {
      override fun isApplicable(subject: SymbolPointer): Boolean = true

      override fun prepareRequest(
        chainBuilder: ModPlanChainBuilder,
        subject: SymbolPointer,
        deletedSubjects: List<SymbolPointer>,
      ): ModPlanChain<ModPlanSafeDeleteProvider.DeletionDecision> =
        chainBuilder.passValue(ModPlanSafeDeleteProvider.DeletionDecision.DELETED)

      override fun analyze(
        chainBuilder: ModPlanChainBuilder,
        subject: SymbolPointer,
        deletedSubjects: List<SymbolPointer>,
      ): ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> =
        chainBuilder.finish { ModPlanAnalysisResult.Found(emptyList(), ModPlanConflicts.EMPTY) }
    }
    ModPlanSafeDeleteProvider.EP_NAME.point.registerExtension(nativeProvider, LoadingOrder.FIRST, testRootDisposable)

    assertEquals(listOf(a.virtualFile), deletedFiles(ready(a)))
    assertEquals(0, delegate.searchCount)
  }

  fun `test prepareForDeletion runs on the copy`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install()

    ready(a)

    val preparedElement = delegate.preparedElements.single()
    assertFalse(preparedElement.isPhysical)
    assertEquals("a.txt", preparedElement.containingFile.name)
  }

  fun `test conflicts of the delegate are broken`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    delegate.conflicts = listOf("legacy conflict")
    install()

    val conflict = ready(a).conflicts.conflicts.single()
    assertEquals(a.virtualFile, conflict.location!!.virtualFile)
    assertEquals(listOf("legacy conflict"), conflict.messages)
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
  }

  fun `test refused target has no questions`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    ExtensionTestUtil.maskExtensions(SafeDeleteProcessorDelegate.EP_NAME, listOf(delegate), testRootDisposable)

    assertTrue(questionsOf(a).isEmpty())
  }

  fun `test an adapter that is legacy only refuses before the search`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    delegate.headlessProvider = object : ModPlanSafeDeleteProvider {
      override fun isApplicable(subject: SymbolPointer): Boolean = true

      override fun isLegacyOnly(subject: SymbolPointer): Boolean = true

      override fun prepareRequest(
        chainBuilder: ModPlanChainBuilder,
        subject: SymbolPointer,
        deletedSubjects: List<SymbolPointer>,
      ): ModPlanChain<ModPlanSafeDeleteProvider.DeletionDecision> =
        chainBuilder.passValue(ModPlanSafeDeleteProvider.DeletionDecision.REFUSED)

      override fun analyze(
        chainBuilder: ModPlanChainBuilder,
        subject: SymbolPointer,
        deletedSubjects: List<SymbolPointer>,
      ): ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> =
        throw AssertionError("A subject that is not supported is not analyzed")
    }
    ExtensionTestUtil.maskExtensions(SafeDeleteProcessorDelegate.EP_NAME, listOf(delegate), testRootDisposable)

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusalOf(a).reason)
    assertEquals(0, delegate.searchCount)
    assertFalse(runReadActionBlocking { ModPlanSafeDeleteRefactoring.getInstance().isSupported(ModPlanRequest(project, listOf(SymbolPointer.of(a)), ModPlanOptionValues.EMPTY)) })
  }

  fun `test preprocess returning null refuses`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    delegate.preprocess = { null }
    install()

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusalOf(a).reason)
  }
}
