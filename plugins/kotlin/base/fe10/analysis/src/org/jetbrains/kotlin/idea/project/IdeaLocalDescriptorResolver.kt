// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.project

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.stubindex.resolve.PluginDeclarationProviderFactory
import org.jetbrains.kotlin.idea.stubindex.resolve.StubBasedPackageMemberDeclarationProvider
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.*
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

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
        val exceptionWithAttachments =
            declarationProviderFactory.safeAs<PluginDeclarationProviderFactory>()?.let { factory ->
                var declarationException = NoDescriptorForDeclarationException(declaration)
                if (declaration is KtClassOrObject && declaration.isTopLevel()) {
                    declaration.fqName?.let { fqName ->
                        val parent = fqName.parent()
                        (factory.createPackageMemberDeclarationProvider(parent) as? StubBasedPackageMemberDeclarationProvider)?.let {
                            try {
                                it.checkClassOrObjectDeclarations(declaration.nameAsSafeName)
                            } catch (e: Exception) {
                                declarationException = NoDescriptorForDeclarationException(declaration, e.message + "\n")
                            }
                        }
                    }
                }

                declarationException.withAttachment("declarationProviderFactory", factory.debugToString())
            } ?: NoDescriptorForDeclarationException(declaration, declarationProviderFactory.toString())
        throw exceptionWithAttachments
            .withPsiAttachment("KtDeclaration.kt", declaration)
            .withAttachment(
                "KtDeclaration location",
                kotlin.runCatching { declaration.containingFile.virtualFile }.getOrNull()
            )
    }

}