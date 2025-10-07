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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinFirConstantExpressionEvaluator
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogPsiResolverUtil.getResolvedDependency
import org.jetbrains.plugins.gradle.util.isInVersionCatalogAccessor

class KotlinAvoidDuplicateDependenciesInspectionVisitor(
    private val holder: ProblemsHolder,
    private val isOnTheFly: Boolean
) : KtVisitorVoid() {
    override fun visitKtFile(file: KtFile) {
        val dependencyBlocks = PsiTreeUtil.findChildrenOfType(file, KtScriptInitializer::class.java)
            .filter { it.text.startsWith("dependencies") }
            .mapNotNull { it.childrenOfType<KtCallExpression>().firstOrNull() }
            .filter {
                analyze(it) {
                    val callableId = it.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId ?: return@analyze false
                    if (callableId.callableName.asString() != "dependencies") return@analyze false
                    if (callableId.packageName != FqName(GRADLE_KOTLIN_PACKAGE)) return@analyze false
                }
                true
            }

        // find all dependencies with their argument type in all the dependencies blocks
        val dependencies = dependencyBlocks.flatMap { it.descendantsOfType<KtCallExpression>() }
            .mapNotNull {
                val dependencyType = findDependencyType(it)
                if (dependencyType == DependencyType.SINGLE_ARGUMENT || dependencyType == DependencyType.NAMED_ARGUMENTS) it to dependencyType
                else null
            }
        // group duplicate dependencies
        val evaluator = KotlinFirConstantExpressionEvaluator()
        val duplicateGroups = dependencies.groupBy { (dependency, type) ->
            // in batch mode only group exact duplicates
            if (isOnTheFly) extractDependencyKey(dependency, type, evaluator)
            else dependency.text
        }.filter { it.key != null && it.value.size > 1 }.map { mapEntry -> mapEntry.key!! to mapEntry.value.map { it.first } }

        duplicateGroups.forEach { (key, dependencies) ->
            if (isOnTheFly) reportProblemInOnTheFlyMode(key.toString(), dependencies)
            else reportProblemInBatchMode(key.toString(), dependencies)
        }
    }

    private fun extractDependencyKey(
        dependency: KtCallExpression,
        type: DependencyType,
        evaluator: KotlinFirConstantExpressionEvaluator
    ): DependencyKey? {
        return when (type) {
            DependencyType.SINGLE_ARGUMENT -> extractSingleArgumentKey(dependency, evaluator)
            DependencyType.NAMED_ARGUMENTS -> extractNamedArgumentsKey(dependency, evaluator)
            else -> null
        }
    }

    private fun extractSingleArgumentKey(dependency: KtCallExpression, evaluator: KotlinFirConstantExpressionEvaluator): DependencyKey? {
        val argumentExpression = dependency.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null

        val stringArgument = evaluator.computeConstantExpression(argumentExpression, false) as? String
        if (stringArgument != null) {
            return DependencyKey(stringArgument, 0)
        }

        if (argumentExpression is KtCallExpression && argumentExpression.calleeExpression?.text == "kotlin") {
            val kotlinArgument = argumentExpression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
            return if (evaluator.computeConstantExpression(kotlinArgument, false) is String) {
                DependencyKey(argumentExpression.text, 0)
            } else null
        }

        if (argumentExpression is KtDotQualifiedExpression) {
            val resolved = argumentExpression.selectorExpression?.mainReference?.resolve() as? PsiMethod ?: return null
            return if (isInVersionCatalogAccessor(resolved)) {
                val dependency = getResolvedDependency(resolved, argumentExpression) ?: return null
                DependencyKey(dependency, 0)
            } else null
        }

        return null
    }

    private fun extractNamedArgumentsKey(dependency: KtCallExpression, evaluator: KotlinFirConstantExpressionEvaluator): DependencyKey? {
        val argList = dependency.valueArgumentList ?: return null

        val group = findNamedOrPositionalArgument(argList, "group", 0)
            ?.let { evaluator.computeConstantExpression(it, false) as? String }
            ?: return null

        val name = findNamedOrPositionalArgument(argList, "name", 1)
            ?.let { evaluator.computeConstantExpression(it, false) as? String }
            ?: return null

        val versionArgument = findNamedOrPositionalArgument(argList, "version", 2)
            ?: return DependencyKey("$group:$name", 0)

        val versionString = evaluator.computeConstantExpression(versionArgument, false) as? String
        if (versionString != null) return DependencyKey("$group:$name:$versionString", 0)

        // check if the version argument is a constant variable
        // if so, put its psi element's hash as the hidden value of the key
        analyze(versionArgument) {
            val resolvedExpression = versionArgument.resolveExpression()
            val hash =
                if (resolvedExpression is KaVariableSymbol && resolvedExpression.isVal) resolvedExpression.psi.hashCode()
                else 0
            return DependencyKey("$group:$name", hash)
        }
    }

    private data class DependencyKey(val main: String, val hidden: Int) {
        override fun toString(): String {
            return main
        }
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
