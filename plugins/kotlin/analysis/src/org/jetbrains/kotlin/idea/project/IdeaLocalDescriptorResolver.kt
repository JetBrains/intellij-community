// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.project

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.stubindex.resolve.PluginDeclarationProviderFactory
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.AbsentDescriptorHandler
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.LocalDescriptorResolver
import org.jetbrains.kotlin.resolve.lazy.NoDescriptorForDeclarationException
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory

class IdeaLocalDescriptorResolver(
    private val resolveElementCache: ResolveElementCache,
    private val absentDescriptorHandler: AbsentDescriptorHandler
) : LocalDescriptorResolver {
    override fun resolveLocalDeclaration(declaration: KtDeclaration): DeclarationDescriptor {
        val context = resolveElementCache.resolveToElement(declaration, BodyResolveMode.FULL)
        return context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, declaration) ?: absentDescriptorHandler.diagnoseDescriptorNotFound(
            declaration
        )
    }
}

class IdeaAbsentDescriptorHandler(
    private val declarationProviderFactory: DeclarationProviderFactory
) : AbsentDescriptorHandler {
    override fun diagnoseDescriptorNotFound(declaration: KtDeclaration): DeclarationDescriptor {
        throw NoDescriptorForDeclarationException(
            declaration,
            (declarationProviderFactory as? PluginDeclarationProviderFactory)?.debugToString()
        )
    }
}