// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.model.SymbolPointer
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.createSmartFileRange
import com.intellij.psi.impl.light.LightElement
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.ElementsForRename
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.SubjectUsage
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.Substitution
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.refactoring.rename.RenameInputValidatorEx
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ModPlanTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ProcessingContext

class ModPlanRenameEngineTest : BasePlatformTestCase() {
  private val readByProvider = ModPlanOption.bool("readByProvider", false) { "Read by the provider" }
  private val askedAfterTarget = ModPlanOption.bool("askedAfterTarget", false) { "Asked after the target" }

  /** What the fake provider was called with, in the order of the calls. */
  private val log = ArrayList<String>()

  private inner class FakeProvider(
    private val applicable: (PsiElement) -> Boolean,
    private val substitute: (PsiElement, ModPlanOptionValues) -> PsiElement?,
    private val alsoRenamed: (PsiElement) -> List<PsiElement>,
    private val targetQuestion: (PsiElement) -> ModPlanQuestion?,
    private val questionsAfterTarget: (PsiElement) -> List<ModPlanQuestion>,
    private val usagesOf: (PsiElement) -> List<PsiElement>,
    private val usagesWithoutPlaceOf: (PsiElement) -> List<PsiElement>,
    private val collisionsOf: (PsiElement) -> List<PsiElement>,
    private val edits: (PsiElement, String) -> ModPlanAnalysisResult,
  ) : ModPlanRenameProvider {
    override fun isOwner(subject: SymbolPointer): Boolean = subject.element()?.let(applicable) == true

    override fun substituteRequest(chainBuilder: ModPlanChainBuilder, subject: SymbolPointer, newName: String): ModPlanChain<Substitution?> {
      val substitute = chainBuilder.request.optionValues().get(ModPlanRenameOptions.SUBSTITUTE_TARGET)
      return chainBuilder.ask(if (substitute) subject.element()?.let(targetQuestion) else null) { afterTarget ->
        val element = subject.element() ?: return@ask afterTarget.passValue(null)
        val renamed = if (substitute) {
          val optionValues = afterTarget.request.optionValues()
          log.add("substitute ${nameOf(element)} readByProvider=${optionValues.get(readByProvider)}")
          substitute(element, optionValues) ?: return@ask afterTarget.passValue(null)
        }
        else {
          element
        }
        val target = if (renamed == element) { subject } else { SymbolPointer.of(renamed) }
        afterTarget.askAll(questionsAfterTarget(renamed)) { afterQuestions -> afterQuestions.passValue(Substitution(target, newName)) }
      }
    }

    override fun prepare(
      chainBuilder: ModPlanChainBuilder,
      subject: SymbolPointer,
      elementsForRename: ElementsForRename,
    ): ModPlanChain<ElementsForRename> {
      val element = subject.element()?.takeIf(applicable) ?: return chainBuilder.passValue(elementsForRename)
      val newName = elementsForRename.newNameOf(subject) ?: return chainBuilder.passValue(elementsForRename)
      log.add("prepare ${nameOf(element)} to $newName")
      return chainBuilder.passValue(alsoRenamed(element).fold(elementsForRename) { prepared, related ->
        prepared.plus(ElementsForRename.of(SymbolPointer.of(related), newName + "2"))
      })
    }

    override fun findUsages(subject: SymbolPointer, elementsForRename: ElementsForRename, request: ModPlanRequest): List<SubjectUsage> {
      val element = subject.element()
      if (element != null && elementsForRename.newNameOf(subject) != null) log.add("search ${nameOf(element)}")
      return element?.let(usagesOf).orEmpty().map { usage -> FakeUsage(usage, usage.createSmartFileRange()) } +
             element?.let(collisionsOf).orEmpty().map { usage -> FakeUsage(usage, usage.createSmartFileRange(), SubjectUsage.CollisionKind.RESOLVABLE) } +
             element?.let(usagesWithoutPlaceOf).orEmpty().map { usage -> FakeUsage(usage, null) }
    }

    override fun renamers(
      subject: SymbolPointer,
      elementsForRename: ElementsForRename,
      factories: List<AutomaticRenamerFactory>,
      usages: List<SubjectUsage>,
    ): List<AutomaticRenamer> {
      val element = subject.element() ?: return emptyList()
      val newName = elementsForRename.newNameOf(subject) ?: return emptyList()
      log.add("renamers ${nameOf(element)} with ${usages.size}")
      return factories.filter { it.isApplicable(element) }.map { it.createRenamer(element, newName, emptyList()) }
    }

    override fun analyze(subject: SymbolPointer, elementsForRename: ElementsForRename, keptUsages: List<SubjectUsage>): ModPlanAnalysisResult {
      val element = subject.element()
      val newName = elementsForRename.newNameOf(subject)
      if (element == null || newName == null) return ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE)
      log.add("edits ${nameOf(element)} to $newName with ${keptUsages.size}")
      val result = edits(element, newName)
      if (result !is ModPlanAnalysisResult.Found) return result
      val usageEdits = keptUsages.map { (it as FakeUsage).element }.distinct().map { ModPlanEdit.delete(it) }
      return ModPlanAnalysisResult.Found(result.edits + usageEdits, result.conflicts)
    }
  }

  private class FakeUsage(
    val element: PsiElement,
    private val place: SmartPsiFileRange?,
    private val collisionKind: SubjectUsage.CollisionKind? = null,
  ) : SubjectUsage {
    override fun place(): SmartPsiFileRange? = place

    override fun collisionKind(): SubjectUsage.CollisionKind? = collisionKind
  }

  /** An element that is gone once [isGone] is set, as an element that a write deletes. */
  private class GoneElement(manager: PsiManager) : LightElement(manager, PlainTextLanguage.INSTANCE) {
    @Volatile
    var isGone: Boolean = false

    override fun isValid(): Boolean = !isGone

    override fun toString(): String = "GoneElement"
  }

  private fun install(
    applicable: (PsiElement) -> Boolean = { true },
    substitute: (PsiElement, ModPlanOptionValues) -> PsiElement? = { element, _ -> element },
    alsoRenamed: (PsiElement) -> List<PsiElement> = { emptyList() },
    targetQuestion: (PsiElement) -> ModPlanQuestion? = { null },
    questionsAfterTarget: (PsiElement) -> List<ModPlanQuestion> = { emptyList() },
    usagesOf: (PsiElement) -> List<PsiElement> = { emptyList() },
    usagesWithoutPlaceOf: (PsiElement) -> List<PsiElement> = { emptyList() },
    collisionsOf: (PsiElement) -> List<PsiElement> = { emptyList() },
    edits: (PsiElement, String) -> ModPlanAnalysisResult = { _, _ -> ModPlanAnalysisResult.Found(emptyList(), ModPlanConflicts.EMPTY) },
    providersBefore: List<ModPlanRenameProvider> = emptyList(),
    providersAfter: List<ModPlanRenameProvider> = emptyList(),
  ) {
    val provider = FakeProvider(applicable, substitute, alsoRenamed, targetQuestion, questionsAfterTarget, usagesOf, usagesWithoutPlaceOf,
                                collisionsOf, edits)
    ExtensionTestUtil.maskExtensions(ModPlanRenameProvider.EP_NAME, providersBefore + provider + providersAfter, testRootDisposable)
  }

  /** A provider that owns no subject. It adds [related] to every rename in [prepare]. The engine must call no other method of it. */
  private class AddingProvider(private val related: PsiElement) : ModPlanRenameProvider {
    override fun isOwner(subject: SymbolPointer): Boolean = false

    override fun substituteRequest(chainBuilder: ModPlanChainBuilder, subject: SymbolPointer, newName: String): ModPlanChain<Substitution?> =
      throw AssertionError("substituteRequest of a provider that is not the owner")

    override fun prepare(
      chainBuilder: ModPlanChainBuilder,
      subject: SymbolPointer,
      elementsForRename: ElementsForRename,
    ): ModPlanChain<ElementsForRename> {
      val newName = elementsForRename.newNameOf(subject) ?: return chainBuilder.passValue(elementsForRename)
      return chainBuilder.passValue(elementsForRename.plus(ElementsForRename.of(SymbolPointer.of(related), newName + "3")))
    }

    override fun findUsages(subject: SymbolPointer, elementsForRename: ElementsForRename, request: ModPlanRequest): List<SubjectUsage> =
      throw AssertionError("findUsages of a provider that is not the owner")

    override fun analyze(subject: SymbolPointer, elementsForRename: ElementsForRename, keptUsages: List<SubjectUsage>): ModPlanAnalysisResult =
      throw AssertionError("analyze of a provider that is not the owner")
  }

  /** A provider that owns every subject and is legacy only for it. The engine must call no other method of it. */
  private class LegacyOnlyProvider : ModPlanRenameProvider {
    override fun isOwner(subject: SymbolPointer): Boolean = true

    override fun isLegacyOnly(subject: SymbolPointer): Boolean = true

    override fun substituteRequest(chainBuilder: ModPlanChainBuilder, subject: SymbolPointer, newName: String): ModPlanChain<Substitution?> =
      throw AssertionError("substituteRequest of a legacy only owner")

    override fun findUsages(subject: SymbolPointer, elementsForRename: ElementsForRename, request: ModPlanRequest): List<SubjectUsage> =
      throw AssertionError("findUsages of a legacy only owner")

    override fun analyze(subject: SymbolPointer, elementsForRename: ElementsForRename, keptUsages: List<SubjectUsage>): ModPlanAnalysisResult =
      throw AssertionError("analyze of a legacy only owner")
  }

  /** A factory of the renamers of [target]. Its renamer renames [related] to `related.txt`, or renames nothing if it is null. */
  private class FakeRenamerFactory(private val target: PsiElement, private val related: PsiNamedElement?) : AutomaticRenamerFactory {
    override fun isApplicable(element: PsiElement): Boolean = element == target

    override fun getOptionName(): String? = null

    override fun isEnabled(): Boolean = true

    override fun setEnabled(enabled: Boolean) {
    }

    override fun createRenamer(element: PsiElement, newName: String, usages: Collection<UsageInfo>): AutomaticRenamer =
      object : AutomaticRenamer() {
        override fun getElements(): List<PsiNamedElement> = listOfNotNull(related)

        override fun getNewName(namedElement: PsiNamedElement): String? = if (namedElement == related) { "related.txt" } else { null }

        override fun hasAnythingToRename(): Boolean = related != null

        override fun getDialogTitle(): String = "Related"

        override fun getDialogDescription(): String = "Related"

        override fun entityName(): String = "Related"
      }
  }

  private fun installRenamerFactories(vararg factories: AutomaticRenamerFactory) {
    ExtensionTestUtil.maskExtensions(AutomaticRenamerFactory.EP_NAME, factories.toList(), testRootDisposable)
  }

  private fun nameOf(element: PsiElement): String = element.containingFile?.name ?: element.toString()

  private fun requestOf(
    target: PsiElement,
    newName: String = "renamed.txt",
    options: ModPlanOptionValues = ModPlanOptionValues.EMPTY,
    substitute: Boolean = true,
  ): ModPlanRequest = runReadActionBlocking { ModPlanRenameRefactoring.request(project, target, newName, substitute, options) }

  private fun plan(
    target: PsiElement,
    newName: String = "renamed.txt",
    options: ModPlanOptionValues = ModPlanOptionValues.EMPTY,
    substitute: Boolean = true,
  ): ModPlan = inBackground {
    val finalStep = ModPlanCommandInterpreter.interpretHeadless(requestOf(target, newName, options, substitute)) { chainBuilder ->
      ModPlanRenameRefactoring.getInstance().chain(chainBuilder).andThen { afterPlan, step -> afterPlan.passValue(step to afterPlan.request) }
    }
    lastFinalStep = finalStep
    finalStep.first.run(finalStep.second)
  }

  /** The final step of the last [plan] with its request: an exclusion runs the step again, as the flow does after a preview. */
  private var lastFinalStep: Pair<ModPlanFinalStep<ModPlan>, ModPlanRequest>? = null

  /** @return the plan of the final step of the last [plan] without [excludedUsages]; call it in [inBackground] */
  private fun planWithout(excludedUsages: List<SmartPsiFileRange>): ModPlan {
    val (finalStep, request) = lastFinalStep!!
    val excluded = runReadActionBlocking { ModPlanRefactoring.excludedUsagesOf(excludedUsages) }
    return finalStep.run(ModPlanChain.withOptionValues(request, request.optionValues().with(ModPlanRefactoring.EXCLUDED_USAGES, excluded)))
  }

  /** Runs the chain of [request] and gives the options of every question that it asks, answering each one true. */
  private fun askedOptions(request: ModPlanRequest): List<List<String>> {
    val asked = ArrayList<List<String>>()
    inBackground {
      ModPlanTestUtil.runAnswering(request, ModPlanRenameRefactoring.getInstance()::chain) { question, currentValues ->
        val form = question as ModPlanQuestion.Form
        val ids = form.options().map { it.id }
        // The engine asks the standard options of the rename for the target. The tests look at the questions of the fake provider.
        if (ids != listOf(ModPlanRenameOptions.SEARCH_IN_COMMENTS.id) && ids != listOf(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES.id)) {
          asked.add(ids)
        }
        form.options().fold(currentValues) { values, option ->
          @Suppress("UNCHECKED_CAST")
          values.with(option as ModPlanOption<Boolean>, true)
        }
      }
    }
    return asked
  }

  fun testNoProviderIsNotSupported() {
    val file = myFixture.configureByText("a.txt", "a")
    install(applicable = { false })

    val refused = plan(file) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refused.reason)
  }

  fun testLegacyOnlyOwnerIsNotSupportedAndTheNextApplicableProviderIsNotAsked() {
    val file = myFixture.configureByText("a.txt", "a")
    install(providersBefore = listOf(LegacyOnlyProvider()))

    val refused = plan(file) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refused.reason)
    assertFalse(inBackground { ModPlanRenameRefactoring.getInstance().isSupported(requestOf(file)) })
    assertEmpty(log)
  }

  fun testNoSubstituteIsNotApplicable() {
    val file = myFixture.configureByText("a.txt", "a")
    install(substitute = { _, _ -> null })

    val refused = plan(file) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.reason)
  }

  fun testHeadlessPlanSubstitutesPreparesAndAnalyzesEveryRenamedSubject() {
    val file = myFixture.configureByText("a.txt", "a")
    val other = myFixture.addFileToProject("b.txt", "b")
    install(alsoRenamed = { listOf(other) })

    assertInstanceOf(plan(file, options = ModPlanOptionValues.EMPTY.with(readByProvider, true)), ModPlan.AcceptedPlan::class.java)

    assertEquals(listOf(
      "substitute a.txt readByProvider=true",
      "prepare a.txt to renamed.txt",
      "search a.txt",
      "search b.txt",
      "edits a.txt to renamed.txt with 0",
      "edits b.txt to renamed.txt2 with 0",
    ), log)
  }

  fun testProviderThatIsNotTheOwnerAddsToTheRename() {
    val file = myFixture.configureByText("a.txt", "a")
    val other = myFixture.addFileToProject("b.txt", "b")
    install(providersAfter = listOf(AddingProvider(other)))

    assertInstanceOf(plan(file), ModPlan.AcceptedPlan::class.java)

    assertContainsElements(log, "edits a.txt to renamed.txt with 0", "edits b.txt to renamed.txt3 with 0")
  }

  fun testTheSubstitutedTargetIsRenamed() {
    val file = myFixture.configureByText("a.txt", "a")
    val base = myFixture.addFileToProject("base.txt", "base")
    install(substitute = { _, _ -> base })

    plan(file)

    assertContainsElements(log, "prepare base.txt to renamed.txt", "edits base.txt to renamed.txt with 0")
  }

  fun testTheTargetIsAskedBeforeTheOptions() {
    val file = myFixture.configureByText("a.txt", "a")
    install(targetQuestion = { ModPlanQuestion.Form.of(listOf(readByProvider), null) },
            questionsAfterTarget = { listOf(ModPlanQuestion.Form.of(listOf(askedAfterTarget), null)) })

    val asked = askedOptions(requestOf(file))

    assertEquals(listOf(listOf(readByProvider.id), listOf(askedAfterTarget.id)), asked)
    assertContainsElements(log, "substitute a.txt readByProvider=true")
  }

  fun testAnAnsweredQuestionIsNotAsked() {
    val file = myFixture.configureByText("a.txt", "a")
    install(targetQuestion = { ModPlanQuestion.Form.of(listOf(readByProvider), null) },
            questionsAfterTarget = { listOf(ModPlanQuestion.Form.of(listOf(askedAfterTarget), null)) })

    val asked = askedOptions(requestOf(file, options = ModPlanOptionValues.EMPTY.with(readByProvider, false)))

    assertEquals(listOf(listOf(askedAfterTarget.id)), asked)
    assertContainsElements(log, "substitute a.txt readByProvider=false")
  }

  /** The legacy rename dialog asks what is renamed itself. */
  fun testRequestWithoutSubstitutionRenamesTheSubjectAndAsksNoTarget() {
    val file = myFixture.configureByText("a.txt", "a")
    val base = myFixture.addFileToProject("base.txt", "base")
    install(substitute = { _, _ -> base }, targetQuestion = { ModPlanQuestion.Form.of(listOf(readByProvider), null) })

    val asked = askedOptions(requestOf(file, substitute = false))
    // The search is the final step of the chain, which the walk of the questions does not run.
    plan(file, substitute = false)

    assertEmpty(asked)
    assertEquals(0, log.count { it.startsWith("substitute") })
    assertContainsElements(log, "prepare a.txt to renamed.txt", "edits a.txt to renamed.txt with 0")
  }

  fun testRefusedEditsRefuseTheRename() {
    val file = myFixture.configureByText("a.txt", "a")
    install(edits = { _, _ -> ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED) })

    val refused = plan(file) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refused.reason)
  }

  fun testConflictsOfEverySubjectAreMerged() {
    val file = myFixture.configureByText("a.txt", "a")
    val other = myFixture.addFileToProject("b.txt", "b")
    install(alsoRenamed = { listOf(other) }, edits = { element, _ ->
      ModPlanAnalysisResult.Found(emptyList(), ModPlanConflicts.builder().broken(element, "conflict of ${nameOf(element)}").build())
    })

    val ready = plan(file) as ModPlan.AcceptedPlan

    assertEquals(listOf("conflict of a.txt", "conflict of b.txt"), ready.conflicts.conflicts.flatMap { it.messages })
  }

  fun testPlanOfNoOccurrenceHasNoUsages() {
    val file = myFixture.configureByText("a.txt", "a")
    install()

    val ready = plan(file, options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true)) as ModPlan.AcceptedPlan

    assertEmpty(ready.usages)
  }

  fun testExcludedUsageIsNotEditedAndNothingIsSearchedAgain() {
    val file = myFixture.configureByText("a.txt", "a")
    val kept = myFixture.addFileToProject("kept.txt", "k")
    val excluded = myFixture.addFileToProject("excluded.txt", "e")
    install(usagesOf = { listOf(kept, excluded) })
    val ready = plan(file) as ModPlan.AcceptedPlan
    val excludedPlace = ready.usages.single { it.virtualFile == excluded.virtualFile }

    val withoutExcluded = inBackground { planWithout(listOf(excludedPlace)) } as ModPlan.AcceptedPlan

    assertEquals(listOf(kept.virtualFile, excluded.virtualFile).sortedBy { it.name }, ready.execution.affectedFiles.sortedBy { it.name })
    assertEquals(listOf(kept.virtualFile), withoutExcluded.execution.affectedFiles)
    assertEquals(listOf(kept.virtualFile), withoutExcluded.usages.map { it.virtualFile })
    assertEquals(1, log.count { it.startsWith("search") })
    assertEquals(listOf("edits a.txt to renamed.txt with 2", "edits a.txt to renamed.txt with 1"), log.filter { it.startsWith("edits") })
  }

  fun testHeadlessRequestWithTheExcludedUsagesOptionPlansWithoutThem() {
    val file = myFixture.configureByText("a.txt", "a")
    val kept = myFixture.addFileToProject("kept.txt", "k")
    val excluded = myFixture.addFileToProject("excluded.txt", "e")
    install(usagesOf = { listOf(kept, excluded) })
    val excludedPlace = (plan(file) as ModPlan.AcceptedPlan).usages.single { it.virtualFile == excluded.virtualFile }
    val options = ModPlanOptionValues.EMPTY.with(
      ModPlanRefactoring.EXCLUDED_USAGES, runReadActionBlocking { ModPlanRefactoring.excludedUsagesOf(listOf(excludedPlace)) })

    val withoutExcluded = plan(file, options = options) as ModPlan.AcceptedPlan

    assertEquals(listOf(kept.virtualFile), withoutExcluded.execution.affectedFiles)
    assertEquals(listOf(kept.virtualFile), withoutExcluded.usages.map { it.virtualFile })
  }

  fun testExclusionAfterAChangeOfPsiSearchesAgain() {
    val file = myFixture.configureByText("a.txt", "a")
    val usage = myFixture.addFileToProject("usage.txt", "u")
    install(usagesOf = { listOf(usage) })
    val ready = plan(file) as ModPlan.AcceptedPlan
    WriteCommandAction.runWriteCommandAction(project) {
      PsiDocumentManager.getInstance(project).getDocument(usage)!!.insertString(0, "more ")
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val plannedAgain = inBackground { planWithout(ready.usages) }

    assertInstanceOf(plannedAgain, ModPlan.AcceptedPlan::class.java)
    assertEquals(2, log.count { it.startsWith("search") })
  }

  fun testTwoSubjectsThatFindOnePlaceGiveOnePlace() {
    val file = myFixture.configureByText("a.txt", "a")
    val other = myFixture.addFileToProject("b.txt", "b")
    val usage = myFixture.addFileToProject("usage.txt", "u")
    install(alsoRenamed = { listOf(other) }, usagesOf = { listOf(usage) })

    val ready = plan(file) as ModPlan.AcceptedPlan

    assertSize(1, ready.usages)
    assertEquals(listOf("edits a.txt to renamed.txt with 1", "edits b.txt to renamed.txt2 with 0"), log.filter { it.startsWith("edits") })
  }

  fun testTwoExclusionsInARowEditNeitherAndSearchOnce() {
    val file = myFixture.configureByText("a.txt", "a")
    val kept = myFixture.addFileToProject("kept.txt", "k")
    val first = myFixture.addFileToProject("first.txt", "f")
    val second = myFixture.addFileToProject("second.txt", "s")
    install(usagesOf = { listOf(kept, first, second) })
    val ready = plan(file) as ModPlan.AcceptedPlan

    val withoutBoth = inBackground {
      planWithout(ready.usages.filter { it.virtualFile == first.virtualFile })
      planWithout(ready.usages.filter { it.virtualFile != kept.virtualFile })
    } as ModPlan.AcceptedPlan

    assertEquals(listOf(kept.virtualFile), withoutBoth.execution.affectedFiles)
    assertEquals(listOf(kept.virtualFile), withoutBoth.usages.map { it.virtualFile })
    assertEquals(1, log.count { it.startsWith("search") })
    assertEquals(listOf("edits a.txt to renamed.txt with 3", "edits a.txt to renamed.txt with 2", "edits a.txt to renamed.txt with 1"),
                 log.filter { it.startsWith("edits") })
  }

  fun testUsageWithoutPlaceIsEditedInEveryPlanAndIsNotAUsageOfThePlan() {
    val file = myFixture.configureByText("a.txt", "a")
    val shown = myFixture.addFileToProject("shown.txt", "s")
    val hidden = myFixture.addFileToProject("hidden.txt", "h")
    install(usagesOf = { listOf(shown) }, usagesWithoutPlaceOf = { listOf(hidden) })
    val ready = plan(file) as ModPlan.AcceptedPlan

    val withoutShown = inBackground { planWithout(ready.usages) } as ModPlan.AcceptedPlan

    assertEquals(listOf(shown.virtualFile), ready.usages.map { it.virtualFile })
    assertEquals(listOf(hidden.virtualFile, shown.virtualFile).sortedBy { it.name }, ready.execution.affectedFiles.sortedBy { it.name })
    assertEmpty(withoutShown.usages)
    assertEquals(listOf(hidden.virtualFile), withoutShown.execution.affectedFiles)
    assertEquals(listOf("edits a.txt to renamed.txt with 2", "edits a.txt to renamed.txt with 1"), log.filter { it.startsWith("edits") })
  }

  fun testCollisionAndReferenceAtOnePlaceReachTheEditsAndAreExcludedTogether() {
    val file = myFixture.configureByText("a.txt", "a")
    val usage = myFixture.addFileToProject("usage.txt", "u")
    install(usagesOf = { listOf(usage) }, collisionsOf = { listOf(usage) })
    val ready = plan(file) as ModPlan.AcceptedPlan

    val withoutPlace = inBackground { planWithout(ready.usages) } as ModPlan.AcceptedPlan

    assertSize(1, ready.usages)
    assertEmpty(withoutPlace.execution.affectedFiles)
    assertEquals(listOf("edits a.txt to renamed.txt with 2", "edits a.txt to renamed.txt with 0"), log.filter { it.startsWith("edits") })
  }

  fun testExcludedPlaceOfTwoSubjectsGoesToNeither() {
    val file = myFixture.configureByText("a.txt", "a")
    val other = myFixture.addFileToProject("b.txt", "b")
    val usage = myFixture.addFileToProject("usage.txt", "u")
    install(alsoRenamed = { listOf(other) }, usagesOf = { listOf(usage) })
    val ready = plan(file) as ModPlan.AcceptedPlan

    val withoutPlace = inBackground { planWithout(ready.usages) } as ModPlan.AcceptedPlan

    assertEmpty(withoutPlace.usages)
    assertEmpty(withoutPlace.execution.affectedFiles)
    assertEquals(listOf("edits a.txt to renamed.txt with 1", "edits b.txt to renamed.txt2 with 0",
                        "edits a.txt to renamed.txt with 0", "edits b.txt to renamed.txt2 with 0"), log.filter { it.startsWith("edits") })
  }

  fun testRefusalOfTheSecondSubjectRefusesThePlanAtIt() {
    val file = myFixture.configureByText("a.txt", "a")
    val other = myFixture.addFileToProject("b.txt", "b")
    install(alsoRenamed = { listOf(other) }, edits = { element, _ ->
      if (element == other) {
        ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED)
      }
      else {
        ModPlanAnalysisResult.Found(emptyList(), ModPlanConflicts.EMPTY)
      }
    })

    val refused = plan(file) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refused.reason)
    assertEquals(other.virtualFile, refused.location?.virtualFile)
  }

  fun testEditsAreInTheOrderOfTheSubjects() {
    val file = myFixture.configureByText("a.txt", "a")
    val other = myFixture.addFileToProject("b.txt", "b")
    val usageOfFile = myFixture.addFileToProject("z.txt", "z")
    val usageOfOther = myFixture.addFileToProject("y.txt", "y")
    install(alsoRenamed = { if (it == file) listOf(other) else emptyList() },
            usagesOf = { if (it == file) listOf(usageOfFile) else listOf(usageOfOther) })

    val ready = plan(file) as ModPlan.AcceptedPlan

    assertEquals(listOf(usageOfFile.virtualFile, usageOfOther.virtualFile), ready.execution.affectedFiles)
  }

  fun testTargetInAReadOnlyContainerIsRefusedBeforeTheSearch() {
    val archive = runReadActionBlocking { psiManager.findFile(myFixture.tempDirFixture.createFile("data.zip"))!! }
    install()

    val refused = plan(archive) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.reason)
    // A binary file has no text range, so the refusal has no location.
    assertNull(refused.location)
    assertContainsElements(log, "substitute data.zip readByProvider=false")
    assertEmpty(log.filter { it.startsWith("prepare") || it.startsWith("search") })
  }

  fun testNameThatAnInputValidatorRefusesIsRefusedWithItsMessage() {
    val file = myFixture.configureByText("a.txt", "a")
    install()
    val validator = object : RenameInputValidatorEx {
      override fun getPattern(): ElementPattern<out PsiElement> = PlatformPatterns.psiFile()

      override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean = true

      override fun getErrorMessage(newName: String, project: Project): String? = if (newName == "bad.txt") "bad name" else null
    }
    ExtensionTestUtil.maskExtensions(RenameInputValidator.EP_NAME, listOf(validator), testRootDisposable)

    val refused = plan(file, newName = "bad.txt") as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.reason)
    assertEquals("bad name", refused.message)
    assertEmpty(log.filter { it.startsWith("prepare") || it.startsWith("search") })
  }

  fun testRenameToTheCurrentNameIsAcceptedWithNoWriteAndSearchesNothing() {
    val file = myFixture.configureByText("a.txt", "a")
    val usage = myFixture.addFileToProject("usage.txt", "u")
    install(usagesOf = { listOf(usage) })
    installRenamerFactories(FakeRenamerFactory(file, null))

    val ready = plan(file, newName = "a.txt",
                     options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.RENAME_RELATED, true)) as ModPlan.AcceptedPlan

    assertTrue(ready.execution.command.isEmpty)
    assertEmpty(ready.execution.affectedFiles)
    assertEmpty(ready.usages)
    assertEmpty(ready.conflicts.conflicts)
    assertEquals(listOf("substitute a.txt readByProvider=false"), log)
  }

  fun testSubjectThatIsGoneWhenTheEditsRunIsNotApplicable() {
    val file = myFixture.configureByText("a.txt", "a")
    val gone = GoneElement(psiManager)
    install(alsoRenamed = { if (it == file) listOf(gone) else emptyList() }, usagesOf = { element ->
      if (element == file) gone.isGone = true
      emptyList()
    })

    val refused = plan(file) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.reason)
    assertContainsElements(log, "search a.txt")
    assertEmpty(log.filter { it.startsWith("edits") })
  }

  fun testWriteEventsReportEveryRenamedElement() {
    val file = myFixture.configureByText("a.txt", "a")
    val other = myFixture.addFileToProject("b.txt", "b")
    install(alsoRenamed = { if (it == file) listOf(other) else emptyList() })
    val ready = plan(file) as ModPlan.AcceptedPlan

    val writeEvent = inBackground { ready.writeEvents.get() }

    val beforeData = writeEvent.beforeData!!
    assertEquals(file, beforeData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY))
    assertEquals(mapOf(file to "renamed.txt", other to "renamed.txt2"), beforeData.getUserData(RefactoringEventData.RENAMES_KEY))
    assertEquals(listOf(file, other), writeEvent.elementChanges.map { it.before })
    assertTrue(writeEvent.elementChanges.all { it.kind == ModPlanRefactoring.ElementChange.Kind.RENAMED })
  }

  fun testWriteEventsReportTheUsagesThatTheUserDidNotExclude() {
    val file = myFixture.configureByText("a.txt", "a")
    val keptUsage = myFixture.addFileToProject("kept.txt", "k")
    val excludedUsage = myFixture.addFileToProject("excluded.txt", "e")
    install(usagesOf = { if (it == file) listOf(keptUsage, excludedUsage) else emptyList() })
    val ready = plan(file) as ModPlan.AcceptedPlan
    val withoutExcluded = inBackground {
      planWithout(ready.usages.filter { it.virtualFile == excludedUsage.virtualFile })
    } as ModPlan.AcceptedPlan

    val usagesOfAllPlan = inBackground { ready.writeEvents.get() }.beforeData!!.getUserData(RefactoringEventData.USAGE_INFOS_KEY)!!
    val usagesOfPlanWithoutExcluded =
      inBackground { withoutExcluded.writeEvents.get() }.beforeData!!.getUserData(RefactoringEventData.USAGE_INFOS_KEY)!!

    assertSameElements(usagesOfAllPlan.map { it.virtualFile }, keptUsage.virtualFile, excludedUsage.virtualFile)
    assertEquals(listOf(keptUsage.virtualFile), usagesOfPlanWithoutExcluded.map { it.virtualFile })
  }

  fun testTargetWithTwoRenamersIsSearchedOnceAndTheRenamersGetItsUsages() {
    val file = myFixture.configureByText("a.txt", "a")
    val usage = myFixture.addFileToProject("usage.txt", "u")
    install(usagesOf = { if (it == file) listOf(usage) else emptyList() })
    installRenamerFactories(FakeRenamerFactory(file, null), FakeRenamerFactory(file, null))

    val ready = plan(file, options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.RENAME_RELATED, true)) as ModPlan.AcceptedPlan

    assertEquals(listOf("search a.txt"), log.filter { it.startsWith("search") })
    assertEquals(listOf("renamers a.txt with 1"), log.filter { it.startsWith("renamers") }.distinct())
    assertEquals(listOf("edits a.txt to renamed.txt with 1"), log.filter { it.startsWith("edits") })
    assertEquals(listOf(usage.virtualFile), ready.usages.map { it.virtualFile })
  }

  /**
   * A search reads the renamed elements, so an element that a renamer adds makes the final step search the target again. The added
   * element is searched once, before the final step, for its collisions.
   */
  fun testElementThatARenamerAddsMakesTheFinalStepSearchTheTargetAgain() {
    val file = myFixture.configureByText("a.txt", "a")
    val related = myFixture.addFileToProject("b.txt", "b")
    val usage = myFixture.addFileToProject("usage.txt", "u")
    install(usagesOf = { if (it == file) listOf(usage) else emptyList() })
    installRenamerFactories(FakeRenamerFactory(file, related))

    val ready = plan(file, options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.RENAME_RELATED, true)) as ModPlan.AcceptedPlan

    assertEquals(listOf("search a.txt", "search b.txt", "search a.txt"), log.filter { it.startsWith("search") })
    assertEquals(listOf("edits a.txt to renamed.txt with 1", "edits b.txt to related.txt with 0"), log.filter { it.startsWith("edits") })
    assertEquals(listOf(usage.virtualFile), ready.usages.map { it.virtualFile })
  }

  fun testChangeOfPsiAfterTheSearchForTheRenamersSearchesAgainInTheFinalStep() {
    val file = myFixture.configureByText("a.txt", "a")
    val usage = myFixture.addFileToProject("usage.txt", "u")
    install(usagesOf = { listOf(usage) })
    installRenamerFactories(FakeRenamerFactory(file, null))
    val request = requestOf(file, options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.RENAME_RELATED, true))
    val finalStep = inBackground {
      ModPlanTestUtil.runAnswering(request, ModPlanRenameRefactoring.getInstance()::chain) { _, currentValues -> currentValues }
    }
    assertEquals(listOf("search a.txt"), log.filter { it.startsWith("search") })
    WriteCommandAction.runWriteCommandAction(project) {
      PsiDocumentManager.getInstance(project).getDocument(usage)!!.insertString(0, "more ")
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val ready = inBackground { finalStep.run(request) } as ModPlan.AcceptedPlan

    assertEquals(listOf("search a.txt", "search a.txt"), log.filter { it.startsWith("search") })
    assertEquals(listOf(usage.virtualFile), ready.usages.map { it.virtualFile })
  }
}
