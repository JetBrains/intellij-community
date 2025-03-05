// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(KaPlatformInterface::class)

package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

internal class KaNotUnderContentRootModuleImpl(
    file: PsiFile?,
    override val project: Project,
) : KaNotUnderContentRootModule {
    private val filePointer = file?.createSmartPointer()

    override val name: String get() = "Non under content root module"
    override val moduleDescription: String get() = name

    override val contentScope: GlobalSearchScope
        get() = file?.let { GlobalSearchScope.fileScope(it) }
            ?: GlobalSearchScope.EMPTY_SCOPE

    override val directDependsOnDependencies: List<KaModule> get() = emptyList()
    override val directFriendDependencies: List<KaModule> get() = emptyList()
    override val directRegularDependencies: List<KaModule> get() = emptyList()
    override val transitiveDependsOnDependencies: List<KaModule> get() = emptyList()

    override val targetPlatform: TargetPlatform get() = JvmPlatforms.defaultJvmPlatform

    override val file: PsiFile? get() = filePointer?.element

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaNotUnderContentRootModuleImpl
                && filePointer == other.filePointer
    }

    override fun hashCode(): Int {
        return filePointer.hashCode()
    }

    override fun toString(): String {
        return this::class.simpleName + " " + file?.let { it::class.simpleName + ", name=${it.name}" + ", virtualFile=" + it.virtualFile }
    }
}