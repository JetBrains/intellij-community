// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.UnresolvedReferenceQuickFixFactory
import org.jetbrains.kotlin.idea.highlighter.Fe10QuickFixProvider
import org.jetbrains.kotlin.idea.highlighter.RegisterQuickFixesLaterIntentionAction
import org.jetbrains.kotlin.idea.inspections.suppress.AnnotationHostKind
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
import org.jetbrains.kotlin.idea.quickfix.KotlinIntentionActionsFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import java.lang.reflect.*
import java.util.*

class Fe10QuickFixProviderImpl : Fe10QuickFixProvider {
    private fun createQuickFixes(
        intentionActionsFactories: Collection<KotlinIntentionActionsFactory>,
        diagnostics: Collection<Diagnostic>,
        actions: MultiMap<Diagnostic, IntentionAction>,
        firstDiagnostic: Diagnostic?,
        replaceUnresolvedReferenceQuickFix: Boolean,
        unresolvedReferenceQuickFixOnly: Boolean
    ): MultiMap<Diagnostic, IntentionAction> {
        val first = diagnostics.first()
        for (intentionActionsFactory in intentionActionsFactories) {
            if ((unresolvedReferenceQuickFixOnly || replaceUnresolvedReferenceQuickFix) && intentionActionsFactory is UnresolvedReferenceQuickFixFactory) {
                if (
                // UnresolvedReferenceQuickFixUpdater works only when reference is available
                    first.psiElement.reference != null &&
                    (unresolvedReferenceQuickFixOnly || intentionActionsFactory.areActionsAvailable(first))
                ) {
                    actions.putValue(first, RegisterQuickFixesLaterIntentionAction)
                    if (unresolvedReferenceQuickFixOnly) break
                    continue
                }
            }
            if (unresolvedReferenceQuickFixOnly) {
                continue
            }
            val allProblemsActions = intentionActionsFactory.createActionsForAllProblems(diagnostics)
            if (allProblemsActions.isNotEmpty()) {
                actions.putValues(firstDiagnostic, allProblemsActions)
            } else {
                for (diagnostic in diagnostics) {
                    actions.putValues(diagnostic, intentionActionsFactory.createActions(diagnostic))
                }
            }
        }

        for (diagnostic in diagnostics) {
            val intentionActions = QuickFixes.getInstance().getActions(diagnostic.factory)
            if (intentionActions.isNotEmpty()) {
                actions.putValues(diagnostic, intentionActions)
            }
        }

        actions.values().forEach { NoDeclarationDescriptorsChecker.check(it::class.java) }

        return actions
    }

    override fun createQuickFixes(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction> =
        createQuickFixes(sameTypeDiagnostics, true, false) { factory: DiagnosticFactory<*> ->
            QuickFixes.getInstance().getActionFactories(factory)
        }

    override fun createPostponedUnresolvedReferencesQuickFixes(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction> =
        createQuickFixes(sameTypeDiagnostics, true, true) { factory: DiagnosticFactory<*> ->
            QuickFixes.getInstance().getActionFactories(factory)
        }

    override fun createUnresolvedReferenceQuickFixes(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction> =
        createQuickFixes(sameTypeDiagnostics, false, false) { factory: DiagnosticFactory<*> ->
            QuickFixes.getInstance().getUnresolvedReferenceActionFactories(factory)
        }

    override fun createUnresolvedReferenceQuickFixesForElement(element: KtElement): Map<PsiElement, Sequence<IntentionAction>> {
        val diagnostics = collectDiagnosticsForElement(element, Severity.ERROR)

        return diagnostics.groupBy { it.psiElement }.mapValues { (_, sameElementDiagnostics) ->
            sameElementDiagnostics.groupBy { it.factory }.asSequence().flatMap { (_, sameTypeDiagnostics) ->
                createUnresolvedReferenceQuickFixes(sameTypeDiagnostics).values()
            }
        }
    }

    private fun collectDiagnosticsForElement(element: KtElement, severity: Severity): List<Diagnostic> {
        val file = element.containingFile
        val bindingContext = element.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
        return bindingContext.diagnostics.filter {
            it.severity == severity && it.psiElement.containingFile == file && it.psiElement.textRange in element.textRange
        }
    }

    private fun createQuickFixes(
        diagnostics: Collection<Diagnostic>,
        replaceUnresolvedReferenceQuickFix: Boolean,
        unresolvedReferenceQuickFixOnly: Boolean,
        intentionActionsFactories: (DiagnosticFactory<*>) -> Collection<KotlinIntentionActionsFactory>
    ): MultiMap<Diagnostic, IntentionAction> {
        val firstDiagnostic = diagnostics.minByOrNull { it.toString() }
        val factory = diagnostics.first().getRealDiagnosticFactory()

        val actions = MultiMap<Diagnostic, IntentionAction>()

        val actionsFactories = intentionActionsFactories(factory)
        return createQuickFixes(
            actionsFactories, diagnostics, actions, firstDiagnostic,
            replaceUnresolvedReferenceQuickFix, unresolvedReferenceQuickFixOnly
        )
    }

    override fun createSuppressFix(element: KtElement, suppressionKey: String, hostKind: AnnotationHostKind): SuppressIntentionAction {
        return KotlinSuppressIntentionAction(element, suppressionKey, hostKind)
    }

    private fun Diagnostic.getRealDiagnosticFactory(): DiagnosticFactory<*> {
        return when (factory) {
            Errors.PLUGIN_ERROR -> Errors.PLUGIN_ERROR.cast(this).a.factory
            Errors.PLUGIN_WARNING -> Errors.PLUGIN_WARNING.cast(this).a.factory
            Errors.PLUGIN_INFO -> Errors.PLUGIN_INFO.cast(this).a.factory
            else -> factory
        }
    }
}

private object NoDeclarationDescriptorsChecker {
    private val LOG = Logger.getInstance(NoDeclarationDescriptorsChecker::class.java)

    private val checkedQuickFixClasses = Collections.synchronizedSet(HashSet<Class<*>>())

    fun check(quickFixClass: Class<*>) {
        if (!checkedQuickFixClasses.add(quickFixClass)) return

        for (field in quickFixClass.declaredFields) {
            checkType(field.genericType, field)
        }

        quickFixClass.superclass?.let { check(it) }
    }

    private fun checkType(type: Type, field: Field) {
        when (type) {
            is Class<*> -> {
                if (
                    DeclarationDescriptor::class.java.isAssignableFrom(type) ||
                    KotlinType::class.java.isAssignableFrom(type) ||
                    Diagnostic::class.java.isAssignableFrom(type)
                ) {
                    LOG.error(
                        "QuickFix class ${field.declaringClass.name} contains field ${field.name} that holds ${type.simpleName}. "
                                + "This leads to holding too much memory through this quick-fix instance. "
                                + "Possible solution can be wrapping it using KotlinIntentionActionFactoryWithDelegate."
                    )
                }

                if (IntentionAction::class.java.isAssignableFrom(type)) {
                    check(type)
                }
            }

            is GenericArrayType -> checkType(type.genericComponentType, field)

            is ParameterizedType -> {
                if (Collection::class.java.isAssignableFrom(type.rawType as Class<*>)) {
                    type.actualTypeArguments.forEach { checkType(it, field) }
                }
            }

            is WildcardType -> type.upperBounds.forEach { checkType(it, field) }
        }
    }
}