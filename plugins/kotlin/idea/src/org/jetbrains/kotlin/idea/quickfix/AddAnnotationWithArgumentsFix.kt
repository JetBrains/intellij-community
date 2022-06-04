// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

// TODO: It might be useful to unify [AddAnnotationWithArgumentsFix] and [AddAnnotationFix] as they do almost the same thing

/**
 * A quick fix to add an annotation with arbitrary arguments to a KtModifierListOwner element.
 *
 * This quick fix is similar to [AddAnnotationFix] but allows to use arbitrary strings as an inner text
 * ([AddAnnotationFix] allows a single class argument).
 *
 * @param element the element to annotate
 * @param annotationFqName the fully qualified name of the annotation class
 * @param arguments the list of strings that should be added as the annotation arguments
 * @param kind the action type that determines the action description text, see [Kind] for details
 * @param useSiteTarget the optional use site target of the annotation (e.g., "@file:Annotation")
 * @param existingAnnotationEntry existing annotation entry (it is updated if not null instead of creating the new annotation)
 */
class AddAnnotationWithArgumentsFix(
    element: KtModifierListOwner,
    private val annotationFqName: FqName,
    private val arguments: List<String>,
    private val kind: Kind = Kind.Self,
    private val useSiteTarget: AnnotationUseSiteTarget? = null,
    private val existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
) : KotlinQuickFixAction<KtModifierListOwner>(element) {

    /**
     * The way to specify the target (the declaration to which the annotation is added) and the source (the declaration
     * from which the annotation, or its template, has been obtained).
     */
    sealed class Kind {
        /**
         * No specification of source and target.
         */
        object Self : Kind()

        /**
         * The target is specified, there is no explicit source.
         */
        class Target(val target: String) : Kind()

        /**
         * Both source and target are specified.
         */
        class Copy(val source: String, val target: String) : Kind()
    }

    override fun getText(): String {
        val annotationName = annotationFqName.shortName().render()
        return when (kind) {
            Kind.Self -> KotlinBundle.message("fix.add.annotation.text.self", annotationName)
            is Kind.Target -> KotlinBundle.message("fix.add.annotation.text.declaration", annotationName, kind.target)
            is Kind.Copy -> KotlinBundle.message("fix.add.annotation.with.arguments.text.copy", annotationName, kind.source, kind.target)
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.annotation.family")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val declaration = element ?: return
        val innerText = if (arguments.isNotEmpty()) arguments.joinToString() else null
        val entry = existingAnnotationEntry?.element
        if (entry != null) {
            if (innerText != null) {
                val psiFactory = KtPsiFactory(declaration)
                entry.valueArgumentList?.addArgument(psiFactory.createArgument(innerText))
                    ?: entry.addAfter(psiFactory.createCallArguments("($innerText)"), entry.lastChild)
            }
        } else {
            declaration.addAnnotation(annotationFqName, innerText, useSiteTarget = useSiteTarget, searchForExistingEntry = false)
        }
    }

    /**
     * A factory for `OVERRIDE_DEPRECATION` warning. It provides an action that copies the `@Deprecated` annotation
     * from the ancestor's deprecated function/property to the overriding function/property in the derived class.
     */
    object CopyDeprecatedAnnotation : KotlinIntentionActionsFactory() {
        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            if (diagnostic.factory != Errors.OVERRIDE_DEPRECATION) return emptyList()

            val deprecation = Errors.OVERRIDE_DEPRECATION.cast(diagnostic)
            val declaration = deprecation.psiElement
            return deprecation.c.mapNotNull {
                val annotation = it.target.annotations.findAnnotation(StandardNames.FqNames.deprecated) ?: return@mapNotNull null
                val arguments = formatDeprecatedAnnotationArguments(annotation)
                val sourceName = renderName(it.target)
                val destinationName = renderName(deprecation.b)
                AddAnnotationWithArgumentsFix(
                    declaration,
                    StandardNames.FqNames.deprecated,
                    arguments,
                    kind = Kind.Copy(sourceName, destinationName)
                )
            }
        }

        private val MESSAGE_ARGUMENT = Name.identifier("message")
        private val REPLACE_WITH_ARGUMENT = Name.identifier("replaceWith")
        private val LEVEL_ARGUMENT = Name.identifier("level")
        private val EXPRESSION_ARGUMENT = Name.identifier("expression")
        private val IMPORTS_ARGUMENT = Name.identifier("imports")

         // A custom pretty-printer for the `@Deprecated` annotation that deals with optional named arguments and varargs.
        private fun formatDeprecatedAnnotationArguments(annotation: AnnotationDescriptor): List<String> {
            val arguments = mutableListOf<String>()
            annotation.allValueArguments[MESSAGE_ARGUMENT]?.safeAs<StringValue>()?.let { arguments.add(it.toString()) }
            val replaceWith = annotation.allValueArguments[REPLACE_WITH_ARGUMENT]?.safeAs<AnnotationValue>()?.value
            if (replaceWith != null) {
                val expression = replaceWith.allValueArguments[EXPRESSION_ARGUMENT]?.safeAs<StringValue>()?.toString()
                val imports = replaceWith.allValueArguments[IMPORTS_ARGUMENT]?.safeAs<ArrayValue>()?.value
                val importsArg = if (imports == null || imports.isEmpty()) "" else (", " + imports.joinToString { it.toString() })
                if (expression != null) {
                    arguments.add("replaceWith = ReplaceWith(${expression}${importsArg})")
                }
            }
            annotation.allValueArguments[LEVEL_ARGUMENT]?.safeAs<EnumValue>()?.let { arguments.add("level = $it") }
            return arguments
        }

        // A renderer for function/property names: uses qualified names when available to disambiguate names of overrides
        private fun renderName(descriptor: DeclarationDescriptor): String {
            val containerPrefix = descriptor.containingDeclaration?.let { "${it.name.render()}." } ?: ""
            val name = descriptor.name.render()
            return containerPrefix + name
        }
    }
}
