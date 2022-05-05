// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import org.jetbrains.kotlin.analysis.api.descriptors.references.base.KtFe10KotlinReferenceProviderContributor

class KotlinReferenceContributor : KotlinReferenceProviderContributor {
    private val delegate = KtFe10KotlinReferenceProviderContributor()
    override fun registerReferenceProviders(registrar: KotlinPsiReferenceRegistrar) {
        delegate.registerReferenceProviders(registrar)

        // TODO: consider moving the following to KtFe10KotlinReferenceProviderContributor
        with(registrar) {

            registerProvider(KotlinDefaultAnnotationMethodImplicitReferenceProvider)
        }
    }
}
