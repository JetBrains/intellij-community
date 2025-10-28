// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogPsiResolverUtil.getResolvedDependency

class KotlinAvoidDuplicateDependenciesInspectionVisitor(
    private val holder: ProblemsHolder,
    private val isOnTheFly: Boolean
) : KtVisitorVoid() {
    override fun visitKtFile(file: KtFile) {
        val dependencyBlocks = file.findScriptInitializers("dependencies").mapNotNull { it.getBlock() }

        // find all dependencies with their argument type in all the dependencies blocks
        val dependencies = dependencyBlocks.flatMap { it.descendantsOfType<KtCallExpression>() }
            .mapNotNull {
                val dependencyType = findDependencyType(it)
                if (dependencyType == DependencyType.SINGLE_ARGUMENT || dependencyType == DependencyType.NAMED_ARGUMENTS) it to dependencyType
                else null
            }

        // group duplicate dependencies
        val duplicateGroups = findDuplicateGroups(dependencies)

        duplicateGroups.forEach { (key, dependencies) ->
            if (isOnTheFly) reportProblemInOnTheFlyMode(key, dependencies)
            else reportProblemInBatchMode(key, dependencies)
        }
    }

    private fun findDuplicateGroups(dependencies: Sequence<Pair<KtCallExpression, DependencyType>>): List<Pair<String, List<KtCallExpression>>> {
        return dependencies.groupBy { (dependency, type) ->
            extractDependencyKey(dependency, type) to createGroupingCriteria(dependency)
        }.filter { it.key.first != null && it.value.size > 1 }
            .map { mapEntry -> mapEntry.key.first!! to mapEntry.value.map { it.first } }
    }

    private fun createGroupingCriteria(dependency: KtCallExpression): Any {
        return if (isOnTheFly) {
            // additionally separate annotationProcessor dependencies
            dependency.calleeExpression?.text == "annotationProcessor"
        } else {
            // in batch mode additionally restrict groups to exact duplicates
            dependency.text
        }
    }

    /**
     * Tries to evaluate the dependency coordinates which act as the key
     *
     * `kotlin(id)` will be evaluated to `"kotlin("resolved-id")"` key
     *
     * Will return null if any part of evaluation fails
     */
    private fun extractDependencyKey(
        dependency: KtCallExpression,
        type: DependencyType
    ): String? {
        return when (type) {
            DependencyType.SINGLE_ARGUMENT -> extractSingleArgumentKey(dependency)
            DependencyType.NAMED_ARGUMENTS -> extractNamedArgumentsKey(dependency)
            else -> null
        }
    }

    private fun extractSingleArgumentKey(dependency: KtCallExpression): String? {
        val argumentExpression = dependency.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null

        // string or direct constant reference to a string argument
        val stringArgument = argumentExpression.evaluateString()
        if (stringArgument != null) {
            return stringArgument
        }

        // kotlin(id) argument
        if (argumentExpression is KtCallExpression && argumentExpression.calleeExpression?.text == "kotlin") {
            val argumentExpressionInKotlin = argumentExpression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
            return argumentExpressionInKotlin.evaluateString()?.let { "kotlin(\"$it\")" }
        }

        // version catalog argument
        if (argumentExpression is KtDotQualifiedExpression) {
            val resolved = argumentExpression.selectorExpression?.mainReference?.resolve() as? PsiMethod ?: return null
            return getResolvedDependency(resolved, argumentExpression).toString()
        }

        return null
    }

    private fun extractNamedArgumentsKey(dependency: KtCallExpression): String? {
        val argList = dependency.valueArgumentList ?: return null

        val group = findNamedOrPositionalArgument(argList, "group", 0)?.evaluateString()
            ?: return null

        val name = findNamedOrPositionalArgument(argList, "name", 1)?.evaluateString()
            ?: return null

        // if the version argument is missing, return a key without a version
        val versionArg = findNamedOrPositionalArgument(argList, "version", 2)
            ?: return "$group:$name"

        val version = versionArg.evaluateString() ?: return null

        return "$group:$name:$version"
    }

    /**
     * Report one exact duplicate element since the quickfix only needs to be invoked once.
     */
    private fun reportProblemInBatchMode(key: String, dependencies: List<KtCallExpression>) {
        val dependency = dependencies.first()
        holder.problem(
            dependency,
            GradleInspectionBundle.message("inspection.message.avoid.duplicate.dependencies.descriptor", key),
        ).fix(RemoveExactDuplicateDependencies(dependencies - dependency))
            .register()
    }

    /**
     * Report each 'similar' duplicate dependency.
     *
     * For each:
     * - offer an intention to navigate to the duplicates.
     * - offer a quick fix to remove only exact duplicates if available.
     */
    private fun reportProblemInOnTheFlyMode(key: String, dependencies: List<KtCallExpression>) {
        dependencies.forEach { dependency ->
            val duplicateDependencies = dependencies - dependency
            val exactDuplicates = duplicateDependencies.filter { it.text == dependency.text }

            val potentialRemoveFix =
                if (exactDuplicates.isNotEmpty()) RemoveExactDuplicateDependencies(exactDuplicates)
                else null

            holder.problem(
                dependency,
                GradleInspectionBundle.message("inspection.message.avoid.duplicate.dependencies.descriptor", key),
            ).maybeFix(potentialRemoveFix)
                .fix(ShowDuplicateElementsFix(key, duplicateDependencies))
                .register()
        }
    }
}

private class ShowDuplicateElementsFix(
    private val dependencyId: String,
    duplicates: List<NavigatablePsiElement>
) : PsiBasedModCommandAction<NavigatablePsiElement>(NavigatablePsiElement::class.java) {
    private val myNavigatablePsiElements = duplicates.map { it.createSmartPointer() }

    override fun getFamilyName(): @IntentionFamilyName String =
        GradleInspectionBundle.message("intention.family.name.show.duplicate.dependencies")

    override fun getPresentation(context: ActionContext, section: NavigatablePsiElement): Presentation {
        val message = GradleInspectionBundle.message("intention.name.show.duplicate.dependencies", dependencyId)
        return Presentation.of(message)
    }

    override fun perform(context: ActionContext, element: NavigatablePsiElement): ModCommand {
        val navigateActions = this.duplicatePsiElements.map { NavigateToAction(it) }
        val message = GradleInspectionBundle.message("intention.choose.action.name.choose.duplicate.dependency")
        return ModCommand.chooseAction(message, navigateActions)
    }

    override fun generatePreview(context: ActionContext?, element: NavigatablePsiElement?): IntentionPreviewInfo {
        val builder = HtmlBuilder()
        val elements = duplicatePsiElements
        for (i in elements.indices) {
            if (i != 0) {
                builder.append(HtmlChunk.br())
            }
            val current = elements[i]
            builder.append(IntentionPreviewInfo.navigatePreviewHtmlChunk(current.getContainingFile(), current.getTextOffset()))
        }
        return IntentionPreviewInfo.Html(builder.toFragment())
    }

    private val duplicatePsiElements: List<NavigatablePsiElement>
        get() = myNavigatablePsiElements.mapNotNull { it.element }

    private class NavigateToAction(
        navigatablePsiElement: NavigatablePsiElement
    ) : PsiBasedModCommandAction<NavigatablePsiElement>(navigatablePsiElement) {

        override fun getFamilyName(): @IntentionFamilyName String =
            GradleInspectionBundle.message("intention.family.name.navigate.to.duplicate.dependency")

        override fun getPresentation(context: ActionContext, element: NavigatablePsiElement): Presentation {
            val lineNumber = element.getContainingFile().getFileDocument().getLineNumber(element.getTextOffset())
            val message = GradleInspectionBundle.message("intention.name.duplicate.dependency.line.number", lineNumber + 1)
            return Presentation.of(message).withHighlighting(element.getTextRange())
        }

        override fun generatePreview(context: ActionContext, element: NavigatablePsiElement): IntentionPreviewInfo {
            return IntentionPreviewInfo.snippet(element)
        }

        override fun perform(context: ActionContext, element: NavigatablePsiElement): ModCommand {
            return ModCommand.select(element)
        }
    }
}

private class RemoveExactDuplicateDependencies(
    dependenciesToRemove: List<KtCallExpression>
) : KotlinModCommandQuickFix<KtCallExpression>() {
    private val dependencyToRemovePointers = dependenciesToRemove.map { it.createSmartPointer() }

    override fun getName(): @IntentionName String = GradleInspectionBundle.message("intention.name.remove.duplicate.dependencies")
    override fun getFamilyName(): @IntentionFamilyName String = name

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val dependenciesToRemove = dependencyToRemovePointers.mapNotNull { updater.getWritable(it.element) }
        dependenciesToRemove.forEach { it.delete() }
    }
}
