// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.resolve

import com.intellij.mock.MockProject
import com.intellij.pom.PomModel
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.pom.tree.TreeAspect
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.caches.trackers.KotlinModuleOutOfCodeBlockModificationTracker
import org.jetbrains.kotlin.idea.caches.trackers.PureKotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.project.IdeaEnvironment
import org.jetbrains.kotlin.idea.project.ResolveElementCache
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.idea.renderer.AbstractDescriptorRendererTest
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.ResolveSession

abstract class AbstractAdditionalResolveDescriptorRendererTest : AbstractDescriptorRendererTest() {
    override fun setUp() {
        super.setUp()

        val mockProject = project as MockProject
        mockProject.registerService(TreeAspect::class.java, TreeAspect())
        mockProject.registerService(PomModel::class.java, PomModelImpl(project))
        mockProject.registerService(PureKotlinCodeBlockModificationListener::class.java, PureKotlinCodeBlockModificationListener(mockProject))
        mockProject.registerService(
            KotlinModuleOutOfCodeBlockModificationTracker.Updater::class.java,
            KotlinModuleOutOfCodeBlockModificationTracker.Updater(mockProject)
        )
        mockProject.registerService(KotlinCodeBlockModificationListener::class.java, KotlinCodeBlockModificationListener(mockProject))
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { unregisterKotlinCodeBlockModificationListener(project as MockProject) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun getDescriptor(declaration: KtDeclaration, container: ComponentProvider): DeclarationDescriptor {
        if (declaration is KtAnonymousInitializer || KtPsiUtil.isLocal(declaration)) {
            return container.get<ResolveElementCache>()
                .resolveToElement(declaration, BodyResolveMode.FULL)
                .get(BindingContext.DECLARATION_TO_DESCRIPTOR, declaration)!!
        }
        return container.get<ResolveSession>().resolveToDescriptor(declaration)
    }

    override val targetEnvironment: TargetEnvironment
        get() = IdeaEnvironment
}
