// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.codeInsight.contributorClass
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.implCommon.handlers.CompletionCharInsertHandler
import org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates.InsertStringTemplateBracesInsertHandler
import org.jetbrains.kotlin.idea.completion.isAtFunctionLiteralStart
import org.jetbrains.kotlin.idea.completion.suppressItemSelectionByCharsOnTyping
import org.jetbrains.kotlin.idea.completion.weighers.CompletionContributorGroupWeigher.groupPriority
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import kotlin.reflect.KClass

/**
 * A completion section is an isolated part of code of a [K2CompletionContributor] that can be run independently.
 * The purpose is to decouple completion sections, allow re-ordering their execution based on their [priority]
 * and potentially run them in parallel.
 * To allow for easier debugging and gathering of performance data, the sections are named with a [name].
 */
internal class K2CompletionSection<P : KotlinRawPositionContext>(
    val priority: K2ContributorSectionPriority,
    val contributor: K2CompletionContributor<P>,
    val name: String,
    val runnable: K2CompletionSectionRunnable<P>,
)

internal class K2CompletionSectionCommonData<P : KotlinRawPositionContext>(
    val completionContext: K2CompletionContext<P>,
    val weighingContext: WeighingContext,
    val prefixMatcher: PrefixMatcher,
    val visibilityChecker: CompletionVisibilityChecker,
    val importStrategyDetector: ImportStrategyDetector,
    val symbolFromIndexProvider: KtSymbolFromIndexProvider,
    val extensionCheckerProvider: () -> KaCompletionExtensionCandidateChecker?,
)

/**
 * This is the context used within a [K2CompletionSection] providing common data that might be
 * shared between contributors running in the same analysis session.
 */
internal class K2CompletionSectionContext<out P : KotlinRawPositionContext>(
    private val commonData: K2CompletionSectionCommonData<P>,
    private val contributor: K2CompletionContributor<P>,
    val sink: K2LookupElementSink,
    private val addLaterSection: (K2CompletionSection<P>) -> Unit,
) {
    val completionContext: K2CompletionContext<P> = commonData.completionContext

    val positionContext: P = completionContext.positionContext

    val parameters: KotlinFirCompletionParameters = completionContext.parameters

    val project: Project = parameters.completionFile.project

    val weighingContext: WeighingContext = commonData.weighingContext

    val prefixMatcher: PrefixMatcher = commonData.prefixMatcher

    val visibilityChecker: CompletionVisibilityChecker = commonData.visibilityChecker

    val importStrategyDetector: ImportStrategyDetector = commonData.importStrategyDetector

    val symbolFromIndexProvider: KtSymbolFromIndexProvider = commonData.symbolFromIndexProvider

    internal val extensionChecker: KaCompletionExtensionCandidateChecker? by lazy { commonData.extensionCheckerProvider() }

    fun completeLaterInSameSession(
        name: String,
        priority: K2ContributorSectionPriority = K2ContributorSectionPriority.DEFAULT,
        runnable: KaSession.(
            context: K2CompletionSectionContext<P>
        ) -> Unit
    ) {
        addLaterSection(
            K2CompletionSection(
                priority = priority,
                contributor = contributor,
                name = name,
                runnable = runnable
            )
        )
    }
}

internal typealias K2CompletionSectionRunnable<P> = KaSession.(context: K2CompletionSectionContext<P>) -> Unit

/**
 * The priority of a completion section determines the order in which the sections are executed.
 * The order only determines the order in which the sections are _started_ to be executed and
 * the order in which elements are added to the [com.intellij.codeInsight.completion.CompletionResultSet].
 *
 * The priorities are ordered by the natural ordering of their respective [value]s.
 * That is to say, smaller values have priority over larger values.
 */
class K2ContributorSectionPriority private constructor(private val value: Double) : Comparable<K2ContributorSectionPriority> {

    companion object {
        /**
         * This priority should be used for sections that offer results that are very likely to be important to the user
         * based on the context _and_ can be executed quickly.
         *
         */
        val HEURISTIC: K2ContributorSectionPriority = K2ContributorSectionPriority(10.0)

        /**
         * This is the priority that should be used for sections that do not conform to the other cases.
         */
        val DEFAULT: K2ContributorSectionPriority = K2ContributorSectionPriority(50.0)

        /**
         * This priority should be used for sections that offer results that are less likely
         * to be useful to the user _and_ may take a long time to be executed.
         */
        val FROM_INDEX: K2ContributorSectionPriority = K2ContributorSectionPriority(100.0)
    }

    override fun compareTo(other: K2ContributorSectionPriority): Int = value.compareTo(other.value)
}

/**
 * The completion setup scope provides information about the current [position] of the completion and allows
 * registering of sections in the [contributor].
 * Please note that it is forbidden to use analysis sessions in this setup scope to avoid capturing the sessions
 * in the [K2CompletionSection]s that might use a different analysis session.
 */
internal class K2CompletionSetupScope<P : KotlinRawPositionContext> internal constructor(
    val completionContext: K2CompletionContext<P>,
    private val contributor: K2CompletionContributor<P>,
    private val registeredCompletions: MutableList<K2CompletionSection<P>>
) {
    val position: P = completionContext.positionContext

    fun completion(
        @NonNls name: String,
        priority: K2ContributorSectionPriority = K2ContributorSectionPriority.DEFAULT,
        runnable: K2CompletionSectionRunnable<P>,
    ) {
        registeredCompletions.add(
            K2CompletionSection(
                priority = priority,
                contributor = contributor,
                name = name,
                runnable = runnable
            )
        )
    }
}

/**
 * A completion contributor that splits completion into several different sections inside the [registerCompletions] method.
 * The purpose of this is to potentially allow executing the sections in parallel.
 * See [K2CompletionSection] for more information.
 */
internal abstract class K2CompletionContributor<P : KotlinRawPositionContext>(
    internal val positionContextClass: KClass<P>
) {
    abstract fun K2CompletionSetupScope<P>.registerCompletions()

    /**
     * Can be changed to not run in certain specific positions that are of type [P] but should still not execute.
     */
    open fun K2CompletionSetupScope<P>.isAppropriatePosition(): Boolean = true

    /**
     * If this method returns false, the execution of the section is skipped.
     * Similar to [isAppropriatePosition], but is executed within the analysis session right before execution of the section.
     */
    open fun KaSession.shouldExecute(context: K2CompletionSectionContext<P>): Boolean = true

    protected fun K2CompletionSectionContext<P>.addElement(element: LookupElement) {
        sink.addElement(decorateLookupElement(element))
    }

    protected fun K2CompletionSectionContext<P>.addElements(elements: Iterable<LookupElement>) {
        val decoratedElements = elements.map { decorateLookupElement(it) }
        sink.addElements(decoratedElements)
    }

    /**
     * Returns the group priority of the completion section that will be applied as weight to the [LookupElement]s
     * from within the [org.jetbrains.kotlin.idea.completion.weighers.CompletionContributorGroupWeigher].
     *
     * Note: this priority only affects the order of the elements displayed to the user.
     *  It does not affect the order in which the sections are executed.
     */
    protected open fun K2CompletionSectionContext<P>.getGroupPriority(): Int = 0

    private fun K2CompletionSectionContext<P>.decorateLookupElement(
        element: LookupElement,
    ): LookupElement {
        element.groupPriority = getGroupPriority()
        element.contributorClass = this::class.java

        if (isAtFunctionLiteralStart(parameters.position)) {
            element.suppressItemSelectionByCharsOnTyping = true
        }

        val bracesInsertHandler = when (parameters.type) {
            KotlinFirCompletionParameters.CorrectionType.BRACES_FOR_STRING_TEMPLATE -> InsertStringTemplateBracesInsertHandler
            else -> WrapSingleStringTemplateEntryWithBracesInsertHandler
        }

        return LookupElementDecorator.withDelegateInsertHandler(
            LookupElementDecorator.withDelegateInsertHandler(element, bracesInsertHandler),
            CompletionCharInsertHandler(parameters.delegate.isAutoPopup),
        )
    }
}

/**
 * A basic completion contributor that only provides a single section that is executed in the [complete] method.
 */
internal abstract class K2SimpleCompletionContributor<P : KotlinRawPositionContext>(
    positionContextClass: KClass<P>,
    private val priority: K2ContributorSectionPriority = K2ContributorSectionPriority.DEFAULT,
    nameOverride: String? = null,
) : K2CompletionContributor<P>(positionContextClass) {
    abstract fun KaSession.complete(context: K2CompletionSectionContext<P>)

    private val name = nameOverride ?: this::class.simpleName ?: "Unknown"

    final override fun K2CompletionSetupScope<P>.registerCompletions() {
        completion(
            name = name,
            priority = priority,
        ) { complete(it) }
    }
}