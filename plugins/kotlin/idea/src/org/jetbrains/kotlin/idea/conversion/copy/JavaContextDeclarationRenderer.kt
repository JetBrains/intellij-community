// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.conversion.copy

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.blockExpressionsOrSingle
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedMemberDescriptor
import org.jetbrains.kotlin.types.KotlinType

internal data class JavaContextDeclarationStubs(
    val localDeclarations: String,
    val memberDeclarations: String
)

/**
 * Converts member/local declarations of the target Kotlin element, into which the Java code is pasted,
 * to Java-friendly stubs ([JavaContextDeclarationStubs]).
 *
 * This is basically a crude Kotlin to Java converter for enhancing the context of plain text copy-paste J2K conversion.
 * In plain text conversion, we don't have an original Java `PsiFile` from which to draw context.
 * So, we do the next best thing: take an approximation of the context from the target Kotlin file.
 */
internal object JavaContextDeclarationRenderer {
    fun render(contextElement: KtElement): JavaContextDeclarationStubs {
        val localDeclarations = getLocalDeclarations(contextElement)
        val memberDeclarations = getMemberDeclarations(contextElement)
        val localDeclarationsJavaStubs = render(localDeclarations)
        val memberDeclarationsJavaStubs = render(memberDeclarations)
        return JavaContextDeclarationStubs(localDeclarationsJavaStubs, memberDeclarationsJavaStubs)
    }

    private fun getLocalDeclarations(contextElement: KtElement): List<DeclarationDescriptor> {
        val containerFunction = contextElement.getParentOfType<KtFunction>(strict = false) ?: return emptyList()
        val localDeclarations = containerFunction.bodyExpression
            ?.blockExpressionsOrSingle()
            ?.filterIsInstance<KtDeclaration>()
            .orEmpty()
        return localDeclarations.mapNotNull { it.resolveToDescriptorIfAny() }.toList()
    }

    private fun getMemberDeclarations(contextElement: KtElement): List<DeclarationDescriptor> {
        val allMembers = contextElement.parentsWithSelf.flatMap { declaration ->
            when (declaration) {
                is KtClass -> declaration.resolveToDescriptorIfAny()
                    ?.unsubstitutedMemberScope
                    ?.getContributedDescriptors()
                    ?.asSequence()

                is KtDeclarationContainer ->
                    declaration.declarations.mapNotNull { it.resolveToDescriptorIfAny() }.asSequence()

                else -> null
            } ?: emptySequence()
        }
        val filteredMembers = allMembers.filter { member ->
            member !is DeserializedMemberDescriptor
                    && !member.name.isSpecial
                    && member.name.asString() != "dummy"
        }

        return filteredMembers.toList()
    }

    private fun render(declarationDescriptors: List<DeclarationDescriptor>): String {
        val renderer = Renderer()
        return buildString {
            for (declaration in declarationDescriptors) {
                val renderedDeclaration = renderer.render(declaration)
                append(renderedDeclaration)
                appendLine()
            }
        }
    }
}

private class Renderer {
    private val builder = StringBuilder()

    fun render(declaration: DeclarationDescriptor): String {
        renderDeclaration(declaration)
        val result = builder.toString()
        builder.clear()
        return result
    }

    private fun renderDeclaration(declaration: DeclarationDescriptor) {
        when (declaration) {
            is VariableDescriptorWithAccessors -> {
                renderType(declaration.type)
                append(" ")
                append(declaration.name.asString())
                append(" = null;")
            }

            is FunctionDescriptor -> {
                renderType(declaration.returnType)
                append(" ")
                append(declaration.name.asString())
                append("(")
                for ((i, parameter) in declaration.valueParameters.withIndex()) {
                    renderType(parameter.type)
                    append(" ")
                    append(parameter.name.asString())
                    if (i != declaration.valueParameters.lastIndex) {
                        append(", ")
                    }
                }
                append(") {}")
            }
        }
    }

    private fun renderType(type: KotlinType?) {
        val fqName = type?.constructor?.declarationDescriptor?.fqNameUnsafe

        if (fqName != null) {
            renderFqName(fqName)
        } else {
            append("Object")
        }
        if (!type?.arguments.isNullOrEmpty()) {
            append("<")
            for (typeArgument in type.arguments) {
                if (typeArgument.isStarProjection) {
                    append("?")
                } else {
                    renderType(typeArgument.type)
                }
            }
            append(">")
        }
    }

    private fun renderFqName(fqName: FqNameUnsafe) {
        val stringFqName = when (fqName) {
            StandardNames.FqNames.unit -> "void"
            else -> JavaToKotlinClassMap.mapKotlinToJava(fqName)?.asFqNameString() ?: fqName.asString()
        }
        append(stringFqName)
    }

    private fun append(s: String) {
        builder.append(s)
    }
}