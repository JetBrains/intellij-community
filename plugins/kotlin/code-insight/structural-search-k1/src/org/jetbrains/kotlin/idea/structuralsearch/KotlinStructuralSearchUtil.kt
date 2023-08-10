// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch

import com.intellij.psi.PsiComment
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import com.intellij.util.asSafely
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.descriptorUtil.classValueType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeAttributes

fun getCommentText(comment: PsiComment): String {
    return when (comment.tokenType) {
        KtTokens.EOL_COMMENT -> comment.text.drop(2).trim()
        KtTokens.BLOCK_COMMENT -> comment.text.drop(2).dropLast(2).trim()
        else -> ""
    }
}

fun KotlinType.renderNames(): Array<String> = arrayOf(
    DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(this),
    DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(this),
    "$this"
)

fun String.removeTypeParameters(): String {
    if (!this.contains('<') || !this.contains('>')) return this
    return this.removeRange(
        this.indexOfFirst { c -> c == '<' },
        this.indexOfLast { c -> c == '>' } + 1
    )
}

val MatchingHandler.withinHierarchyTextFilterSet: Boolean
    get() = this is SubstitutionHandler && (this.isSubtype || this.isStrictSubtype)

fun KtDeclaration.resolveDeclType(): KotlinType? = (resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType

fun KtExpression.resolveReceiverType(): KotlinType? {
    val descriptor = resolveToCall()?.resultingDescriptor?.containingDeclaration
    if (descriptor is ClassDescriptor) return descriptor.classValueType ?: descriptor.defaultType
    return null
}

fun KtExpression.resolveExprType(): KotlinType? {
    val descriptor = resolveMainReferenceToDescriptors().firstOrNull()
    if (descriptor is ClassDescriptor) return descriptor.classValueType ?: descriptor.defaultType
    if (descriptor is PropertyDescriptor) return descriptor.returnType
    if (this is KtDotQualifiedExpression && parent is KtDotQualifiedExpression) return parent.asSafely<KtExpression>()?.resolveReceiverType()
    return resolveType()
}

fun ClassDescriptor.toSimpleType(nullable: Boolean = false) =
    KotlinTypeFactory.simpleType(TypeAttributes.Empty, this.typeConstructor, emptyList(), nullable)
