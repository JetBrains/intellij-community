// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.highlighter.AnnotationHostKind
import org.jetbrains.kotlin.idea.highlighter.Fe10QuickFixProvider
import org.jetbrains.kotlin.idea.quickfix.KotlinSuppressIntentionAction
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.types.KotlinType
import java.lang.reflect.*
import java.util.*

class Fe10QuickFixProviderImpl : Fe10QuickFixProvider {
    override fun createQuickFixes(diagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction> {
        val first = diagnostics.minByOrNull { it.toString() }
        val factory = diagnostics.first().getRealDiagnosticFactory()

        val actions = MultiMap<Diagnostic, IntentionAction>()

        val intentionActionsFactories = QuickFixes.getInstance().getActionFactories(factory)
        for (intentionActionsFactory in intentionActionsFactories) {
            val allProblemsActions = intentionActionsFactory.createActionsForAllProblems(diagnostics)
            if (allProblemsActions.isNotEmpty()) {
                actions.putValues(first, allProblemsActions)
            } else {
                for (diagnostic in diagnostics) {
                    actions.putValues(diagnostic, intentionActionsFactory.createActions(diagnostic))
                }
            }
        }

        for (diagnostic in diagnostics) {
            actions.putValues(diagnostic, QuickFixes.getInstance().getActions(diagnostic.factory))
        }

        actions.values().forEach { NoDeclarationDescriptorsChecker.check(it::class.java) }

        return actions
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
                if (DeclarationDescriptor::class.java.isAssignableFrom(type) || KotlinType::class.java.isAssignableFrom(type)) {
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