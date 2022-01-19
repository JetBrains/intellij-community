// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

@Suppress("UnstableApiUsage")
class KotlinInlayParameterHintsProvider : InlayParameterHintsProvider {

    override fun getDefaultBlackList(): Set<String> =
        setOf(
            "*listOf", "*setOf", "*arrayOf", "*ListOf", "*SetOf", "*ArrayOf", "*assert*(*)", "*mapOf", "*MapOf",
            "kotlin.require*(*)", "kotlin.check*(*)", "*contains*(value)", "*containsKey(key)", "kotlin.lazyOf(value)",
            "*SequenceBuilder.resume(value)", "*SequenceBuilder.yield(value)",

            /* Gradle DSL especially annoying hints */
            "org.gradle.api.Project.hasProperty(propertyName)",
            "org.gradle.api.Project.findProperty(propertyName)",
            "org.gradle.api.Project.file(path)",
            "org.gradle.api.Project.uri(path)",
            "jvmArgs(arguments)",
            "org.gradle.kotlin.dsl.DependencyHandlerScope.*(notation)",
            "org.gradle.kotlin.dsl.*(dependencyNotation)",
            "org.gradle.kotlin.dsl.kotlin(module)",
            "org.gradle.kotlin.dsl.kotlin(module,version)",
            "org.gradle.kotlin.dsl.project(path,configuration)"
        )

    override fun getSettingsPreview(): String {
        return """
            fun callsAnotherFun() {
                anotherFunction(1, 2)            
            }
            
            fun anotherFunction(a: Int = 10, b: Int = 5): Int {
                val sum = a + b
                return sum * 2
            } 
        """.trimIndent()
    }

    override fun getHintInfo(element: PsiElement): HintInfo? {
        if (!(HintType.PARAMETER_HINT.isApplicable(element))) return null
        val parent: PsiElement = (element as? KtValueArgumentList)?.parent ?: return null
        return (parent as? KtCallElement)?.let { getMethodInfo(it) }
    }

    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        return if (HintType.PARAMETER_HINT.isApplicable(element))
            HintType.PARAMETER_HINT.provideHints(element)
        else emptyList()
    }

    override fun getBlackListDependencyLanguage(): Language = JavaLanguage.INSTANCE

    override fun getInlayPresentation(inlayText: String): String = inlayText

    private fun getMethodInfo(elem: KtCallElement): HintInfo.MethodInfo? {
        val resolvedCall = elem.resolveToCall()
        val resolvedCallee = resolvedCall?.candidateDescriptor
        if (resolvedCallee is FunctionDescriptor) {
            val paramNames =
                resolvedCallee.valueParameters.asSequence().map { it.name }.filter { !it.isSpecial }.map(Name::asString).toList()
            val fqName = if (resolvedCallee is ConstructorDescriptor)
                resolvedCallee.containingDeclaration.fqNameSafe.asString()
            else
                (resolvedCallee.fqNameOrNull()?.asString() ?: return null)
            return HintInfo.MethodInfo(fqName, paramNames)
        }
        return null
    }
}

fun PsiElement.isNameReferenceInCall() =
    this is KtNameReferenceExpression && parent is KtCallExpression