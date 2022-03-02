// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.kdoc.KDocReferenceDescriptorsImpl
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.references.ReferenceAccess

class KotlinReferenceContributor : KotlinReferenceProviderContributor {
    override fun registerReferenceProviders(registrar: KotlinPsiReferenceRegistrar) {
        with(registrar) {
            registerProvider(factory = ::KtSimpleNameReferenceDescriptorsImpl)

            registerMultiProvider<KtNameReferenceExpression> { nameReferenceExpression ->
                if (nameReferenceExpression.getReferencedNameElementType() != KtTokens.IDENTIFIER) {
                    return@registerMultiProvider PsiReference.EMPTY_ARRAY
                }
                if (nameReferenceExpression.parents.any { it is KtImportDirective || it is KtPackageDirective || it is KtUserType }) {
                    return@registerMultiProvider PsiReference.EMPTY_ARRAY
                }

                when (nameReferenceExpression.readWriteAccess(useResolveForReadWrite = false)) {
                    ReferenceAccess.READ ->
                        arrayOf(SyntheticPropertyAccessorReferenceDescriptorImpl(nameReferenceExpression, getter = true))
                    ReferenceAccess.WRITE ->
                        arrayOf(SyntheticPropertyAccessorReferenceDescriptorImpl(nameReferenceExpression, getter = false))
                    ReferenceAccess.READ_WRITE ->
                        arrayOf(
                            SyntheticPropertyAccessorReferenceDescriptorImpl(nameReferenceExpression, getter = true),
                            SyntheticPropertyAccessorReferenceDescriptorImpl(nameReferenceExpression, getter = false)
                        )
                }
            }

            registerProvider(factory = ::KtConstructorDelegationReferenceDescriptorsImpl)

            registerProvider(factory = ::KtInvokeFunctionReferenceDescriptorsImpl)

            registerProvider(factory = ::KtArrayAccessReferenceDescriptorsImpl)

            registerProvider(factory = ::KtCollectionLiteralReferenceDescriptorsImpl)

            registerProvider(factory = ::KtForLoopInReferenceDescriptorsImpl)

            registerProvider(factory = ::KtPropertyDelegationMethodsReferenceDescriptorsImpl)

            registerProvider(factory = ::KtDestructuringDeclarationReferenceDescriptorsImpl)

            registerProvider(factory = ::KDocReferenceDescriptorsImpl)

            registerProvider(KotlinDefaultAnnotationMethodImplicitReferenceProvider)
        }
    }
}
