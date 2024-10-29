// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.icons.AllIcons
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.platforms.KotlinCommonLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.isCompatibleWith
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.isCommon
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

abstract class AbstractCommonIdePlatformKindTooling : IdePlatformKindTooling() {
    private companion object {
        // TODO: KotlinCommonMavenConfigurator
        private const val MAVEN_COMMON_STDLIB_ID = "kotlin-stdlib-common"
    }

    override val kind: CommonIdePlatformKind get() = CommonIdePlatformKind

    override val mavenLibraryIds: List<String> = listOf(MAVEN_COMMON_STDLIB_ID)
    override val gradlePluginId get() = "kotlin-platform-common"
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.COMMON)

    override val libraryKind: KotlinCommonLibraryKind get() = KotlinCommonLibraryKind

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        val platform = declaration.module?.platform

        val icons = getInstances()
            .filter { it != this && (platform == null || it.kind.isCompatibleWith(platform)) }
            .mapNotNull { it.getTestIcon(declaration, allowSlowOperations) }
            .distinct()

        return when (icons.size) {
            0 -> null
            else -> icons.singleOrNull() ?: AllIcons.RunConfigurations.TestState.Run
        }
    }

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        val module = function.containingKtFile.module ?: return false
        return module.implementingModules.any { implementingModule ->
            implementingModule.platform.idePlatformKind.takeIf { !it.isCommon }?.tooling?.acceptsAsEntryPoint(function) ?: false
        }
    }
}