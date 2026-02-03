// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.gradle.service.resolve.GradleProjectReference

private const val GRADLE_SEPARATOR = ":"
private const val GRADLE_CLASS_ACCESSOR_SEPARATOR = "_"
private const val GRADLE_ROOT_PROJECT_ACCESSOR = "RootProjectAccessor"
private const val GRADLE_PROJECT_DEPENDENCY_SUFFIX = "ProjectDependency"
private val GRADLE_DSL_PROJECT: Name = Name.identifier("project")
private val GRADLE_DSL_PROJECTS: Name = Name.identifier("projects")
private val GRADLE_PROJECT_PACKAGE = FqName("org.gradle.accessors.dm")
private val KOTLIN_DEPENDENCY_HANDLER_CLASS = FqName("KotlinDependencyHandler")

class KotlinGradleProjectReferenceProvider: AbstractKotlinGradleReferenceProvider() {
    override fun getImplicitReference(
        element: PsiElement,
        offsetInElement: Int
    ): PsiSymbolReference? {
        val parent = (element.parent as? KtElement)?.takeIf { it.containingKtFile.isScript() } ?: return null

        getProjectAccessors(parent)?.let { projectAccessors ->
            return GradleProjectReference(element, TextRange(0, element.textRange.length), projectAccessors)
        }

        val text = getTextFromLiteralEntry(parent)
            ?.takeIf { it.startsWith(GRADLE_SEPARATOR) } ?: return null
        val callableId = analyzeSurroundingCallExpression(element.parent) ?: return null

        if (callableId.callableName != GRADLE_DSL_PROJECT) return null

        // either from pure gradle dsl
        if (!(callableId.packageName == GRADLE_DSL_PACKAGE ||
                    // or from kotlin gradle plugin
                    (callableId.packageName == KGP_PACKAGE && callableId.className == KOTLIN_DEPENDENCY_HANDLER_CLASS))
        ) {
            return null
        }

        val length = element.textRange.length
        return if (text == GRADLE_SEPARATOR) {
            // in this special case, we want the root project reference to span over the colon symbol
            GradleProjectReference(element, TextRange(0, length), emptyList())
        } else {
            GradleProjectReference(element, TextRange(1, length), text.substring(1).split(GRADLE_SEPARATOR))
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun getProjectAccessors(element: PsiElement): List<String>? {
        val dotQualifiedExpression = element.getParentOfType<KtDotQualifiedExpression>(true, KtDeclarationWithBody::class.java) ?: return null
        val (variableCallableId, functionCallableId) =
            allowAnalysisOnEdt {
                analyze(dotQualifiedExpression) {
                    val elementCallableId = (element as? KtElement)?.resolveToCall()?.singleVariableAccessCall()?.symbol?.callableId
                    if (elementCallableId?.packageName == GRADLE_DSL_PACKAGE && elementCallableId.callableName == GRADLE_DSL_PROJECTS) return emptyList()

                    val resolveCallOld = dotQualifiedExpression.resolveToCall()
                    val variableAccessCall = resolveCallOld?.singleVariableAccessCall()
                    val functionCall = resolveCallOld?.singleFunctionCallOrNull()
                    variableAccessCall?.symbol?.callableId to functionCall?.symbol?.callableId
                }
            }
        val packageName = (variableCallableId ?: functionCallableId)?.packageName
        if (packageName != GRADLE_PROJECT_PACKAGE) return null

        val className = (variableCallableId ?: functionCallableId)?.className?.asString() ?: return null

        val identifier = variableCallableId?.callableName?.identifier ?: functionCallableId?.callableName?.identifier?.getterToPropertyName() ?: return null
        if (className == GRADLE_ROOT_PROJECT_ACCESSOR) return listOf(identifier)

        val modules = className.dropSuffix(GRADLE_PROJECT_DEPENDENCY_SUFFIX) ?: return null
        return modules.split(GRADLE_CLASS_ACCESSOR_SEPARATOR)
            .map { it.first().lowercaseChar() + it.drop(1).camelToKebabCase() } + identifier.camelToKebabCase()
    }

    fun String.camelToKebabCase(): String =
        this.fold(StringBuilder()) { acc, c ->
            val lc = c.lowercase()
            if (c.isUpperCase()) acc.append('-')
            acc.append(lc)
        }.toString()

    private fun String.dropPrefix(prefix: String): String? =
        takeIf { it.startsWith(prefix) }?.replaceFirst(prefix, "")

    private fun String.dropSuffix(suffix: String): String? =
        takeIf { it.endsWith(suffix) }?.dropLast(suffix.length)

    private fun String.getterToPropertyName(): String? =
        dropPrefix("get")?.takeIf { it.isNotEmpty() }?.let {
            it.first().lowercaseChar() + it.substring(1)
        }
}