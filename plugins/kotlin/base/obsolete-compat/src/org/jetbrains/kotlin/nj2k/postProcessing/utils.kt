// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.postProcessing

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression

@Deprecated(
    "Deprecated as internal Java to Kotlin converter utility",
    replaceWith = ReplaceWith("(resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType")
)
fun KtDeclaration.type() =
    (resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType

@Deprecated(
    "Deprecated as internal Java to Kotlin converter utility",
    replaceWith = ReplaceWith("mainReference.resolve()")
)
fun KtReferenceExpression.resolve() =
    mainReference.resolve()
