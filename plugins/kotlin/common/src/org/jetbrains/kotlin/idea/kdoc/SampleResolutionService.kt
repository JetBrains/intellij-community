// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.kdoc

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.application.getService
import org.jetbrains.kotlin.resolve.BindingContext

interface SampleResolutionService {
    fun resolveSample(
        context: BindingContext,
        fromDescriptor: DeclarationDescriptor,
        resolutionFacade: ResolutionFacade,
        qualifiedName: List<String>
    ): Collection<DeclarationDescriptor>

    companion object {

        /**
         * It's internal implementation, please use [resolveKDocSampleLink], or [resolveKDocLink]
         */
        internal fun resolveSample(
            context: BindingContext,
            fromDescriptor: DeclarationDescriptor,
            resolutionFacade: ResolutionFacade,
            qualifiedName: List<String>
        ): Collection<DeclarationDescriptor> {
            val instance = resolutionFacade.project.getService<SampleResolutionService>()
            return instance?.resolveSample(context, fromDescriptor, resolutionFacade, qualifiedName) ?: emptyList()
        }
    }
}
