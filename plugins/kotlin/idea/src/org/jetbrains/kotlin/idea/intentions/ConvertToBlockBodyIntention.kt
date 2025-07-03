// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyContext
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.intentions.ConvertToBlockBodyIntention.Holder.convert
import org.jetbrains.kotlin.idea.intentions.ConvertToBlockBodyIntention.Holder.createContext
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.isNullabilityFlexible
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

class ConvertToBlockBodyIntention : SelfTargetingIntention<KtDeclarationWithBody>(
    KtDeclarationWithBody::class.java,
    KotlinBundle.lazyMessage("convert.to.block.body")
) {
    override fun isApplicableTo(element: KtDeclarationWithBody, caretOffset: Int) = createContext(element) != null

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) =
        element is KtDeclaration || super.skipProcessingFurtherElementsAfter(element)

    override fun applyTo(element: KtDeclarationWithBody, editor: Editor?) {
        convert(element, true)
    }

    object Holder {
        fun convert(declaration: KtDeclarationWithBody, withReformat: Boolean = false): KtDeclarationWithBody {
            val context = createContext(declaration, withReformat) ?: return declaration
            ConvertToBlockBodyUtils.convert(declaration, context)
            return declaration
        }

        fun createContext(declaration: KtDeclarationWithBody, reformat: Boolean = false): ConvertToBlockBodyContext? {
            if (!ConvertToBlockBodyUtils.isConvertibleByPsi(declaration)) return null

            val body = declaration.bodyExpression ?: return null

            val returnType = declaration.returnType() ?: return null
            if (returnType.isError && declaration is KtNamedFunction && !declaration.hasDeclaredReturnType())  {
                return null
            }

            val bodyType = body.analyze().getType(body)

            return ConvertToBlockBodyContext(
                returnTypeIsUnit = returnType.isUnit(),
                returnTypeIsNothing = returnType.isNothing(),
                returnTypeString = IdeDescriptorRenderers.SOURCE_CODE.renderType(returnType),
                bodyTypeIsUnit = bodyType?.isUnit() == true,
                bodyTypeIsNothing = bodyType?.isNothing() == true,
                reformat = reformat
            )
        }

        private fun KtDeclarationWithBody.returnType(): KotlinType? {
            return when (this) {
                is KtNamedFunction -> {
                    val descriptor = resolveToDescriptorIfAny()
                    val returnType = descriptor?.returnType ?: return null
                    if (returnType.isNullabilityFlexible()
                        && descriptor.overriddenDescriptors.firstOrNull()?.returnType?.isMarkedNullable == false
                    ) returnType.makeNotNullable() else returnType
                }
                is KtPropertyAccessor -> {
                    val parent = this.parent as? KtProperty
                    (parent?.resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType
                }
                else -> null
            }
        }
    }
}
