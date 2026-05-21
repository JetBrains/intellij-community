// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.gradle.scripting.shared.isGradleKotlinScript
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_FILE_SYSTEM_LOCATION_PROPERTY
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER

/**
 * Registers narrowly scoped Kotlin DSL quick-fixes for breaking Gradle API migrations (IDEA-384547).
 *
 * These fixes intentionally handle only common lazy-property migration shapes that become unresolved method calls after a Gradle
 * upgrade. Broad Property/Provider rewrites can change Gradle lazy configuration semantics and should stay out of this registrar.
 */
internal class KotlinGradlePropertyMethodCallQuickFixRegistrar : KotlinQuickFixRegistrar() {

    private val fixes = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(UNRESOLVED_REFERENCE_FACTORY)
        registerFactory(UNRESOLVED_REFERENCE_WRONG_RECEIVER_FACTORY)
    }

    override val list: KotlinQuickFixesList = KotlinQuickFixesList.createCombined(fixes)
}

private val UNRESOLVED_REFERENCE_FACTORY =
    KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
        listOfNotNull(createGradlePropertyMethodCallQuickFix(diagnostic.psi))
    }

private val UNRESOLVED_REFERENCE_WRONG_RECEIVER_FACTORY =
    KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReferenceWrongReceiver ->
        listOfNotNull(createGradlePropertyMethodCallQuickFix(diagnostic.psi))
    }

context(_: KaSession)
private fun createGradlePropertyMethodCallQuickFix(psi: PsiElement): ModCommandAction? {
    val qualifiedExpression = psi.gradlePropertyMethodCall()
        ?: return null

    if (!isGradleKotlinScript(qualifiedExpression.containingKtFile.alwaysVirtualFile)) {
        return null
    }

    val receiverType = qualifiedExpression.receiverExpression.expressionType ?: return null

    val isGradleFilePropertyType = receiverType.isSubtypeOf(topLevelClassId(GRADLE_API_FILE_SYSTEM_LOCATION_PROPERTY))
    val isGradleProviderType = receiverType.isSubtypeOf(topLevelClassId(GRADLE_API_PROVIDER_PROVIDER))

    return when {
        isGradleFilePropertyType -> {
            if (qualifiedExpression.hasFileCallableSelector()) {
                UnwrapGradlePropertyMethodCallQuickFix(qualifiedExpression, GradlePropertyUnwrap.GET_AS_FILE)
            } else {
                null
            }
        }

        isGradleProviderType -> {
            UnwrapGradlePropertyMethodCallQuickFix(qualifiedExpression, GradlePropertyUnwrap.GET)
        }

        else -> null
    }
}

private class UnwrapGradlePropertyMethodCallQuickFix(
    element: KtDotQualifiedExpression,
    private val unwrap: GradlePropertyUnwrap,
) : PsiUpdateModCommandAction<KtDotQualifiedExpression>(element), DumbAware {

    override fun getFamilyName(): @IntentionFamilyName String =
        GradleInspectionBundle.message(unwrap.familyNameKey)

    override fun getPresentation(
        context: ActionContext,
        element: KtDotQualifiedExpression,
    ): Presentation = Presentation.of(familyName).withPriority(PriorityAction.Priority.HIGH)

    override fun invoke(
        actionContext: ActionContext,
        element: KtDotQualifiedExpression,
        updater: ModPsiUpdater,
    ) {
        val callExpression = element.selectorExpression as? KtCallExpression ?: return
        val replacement = KtPsiFactory(actionContext.project).createExpressionByPattern(
            unwrap.expressionPattern,
            element.receiverExpression,
            callExpression,
        )
        element.replace(replacement)
    }
}

private fun PsiElement.gradlePropertyMethodCall(): KtDotQualifiedExpression? {
    val callExpression = when (this) {
        is KtNameReferenceExpression -> parent as? KtCallExpression
        is KtCallExpression -> this
        is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression
        else -> parentOfType<KtCallExpression>(withSelf = true)
    } ?: return null

    if (callExpression.calleeExpression !is KtNameReferenceExpression) return null
    if (this is KtNameReferenceExpression && callExpression.calleeExpression != this) return null

    val qualifiedExpression = callExpression.parent as? KtDotQualifiedExpression ?: return null
    if (qualifiedExpression.selectorExpression != callExpression) return null

    return qualifiedExpression
}

// Checks whether the unresolved selector would become callable after unwrapping the Gradle property to java.io.File.
private fun KtDotQualifiedExpression.hasFileCallableSelector(): Boolean {
    val callExpression = selectorExpression as? KtCallExpression ?: return false
    val calleeName = (callExpression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return false
    val fileClass = JavaPsiFacade.getInstance(project).findClass(JAVA_IO_FILE_FQN, resolveScope)
    if (fileClass?.findMethodsByName(calleeName, true)?.isNotEmpty() == true) return true
    return hasKotlinIoFileExtensionFunction(calleeName)
}

private fun KtDotQualifiedExpression.hasKotlinIoFileExtensionFunction(calleeName: String): Boolean {
    // Gradle script context does not reliably expose kotlin.io File extensions through Kotlin extension indexes.
    // Check the stdlib JVM facade instead; these functions are available to Kotlin as default imports.
    val filesClass = JavaPsiFacade.getInstance(project)
        .findClass(KOTLIN_IO_FILES_KT_FQN, GlobalSearchScope.allScope(project))
        ?: return false
    return filesClass.findMethodsByName(calleeName, true).any { method ->
        method.parameterList.parameters.firstOrNull()?.type?.canonicalText == JAVA_IO_FILE_FQN
    }
}

private const val JAVA_IO_FILE_FQN = "java.io.File"
private const val KOTLIN_IO_FILES_KT_FQN = "kotlin.io.FilesKt"

private fun topLevelClassId(fqn: String): ClassId = ClassId.topLevel(FqName(fqn))

private enum class GradlePropertyUnwrap(
    val familyNameKey: String,
    val expressionPattern: String,
) {
    GET("intention.name.gradle.property.method.call.unwrap", "$0.get().$1"),
    GET_AS_FILE("intention.name.gradle.file.property.method.call.unwrap", "$0.get().asFile.$1"),
}
