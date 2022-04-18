// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.kdoc

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.resolve.BindingContext

interface KDocLinkResolutionService {
    fun resolveKDocLink(
        context: BindingContext,
        fromDescriptor: DeclarationDescriptor,
        resolutionFacade: ResolutionFacade,
        qualifiedName: List<String>
    ): Collection<DeclarationDescriptor>
}
