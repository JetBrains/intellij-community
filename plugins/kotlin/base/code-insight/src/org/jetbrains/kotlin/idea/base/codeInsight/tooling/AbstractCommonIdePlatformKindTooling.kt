// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
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
        private val FAST_COMPUTED_TEST_ICON_KEY: Key<Icon> = Key.create<Icon>("FAST_COMPUTED_TEST_ICON_KEY")
    }

    override val kind: CommonIdePlatformKind get() = CommonIdePlatformKind

    override val mavenLibraryIds: List<String> = listOf(MAVEN_COMMON_STDLIB_ID)
    override val gradlePluginId get() = "kotlin-platform-common"
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.COMMON)

    override val libraryKind: KotlinCommonLibraryKind get() = KotlinCommonLibraryKind

    /**
     * Assume that `!allowSlowOperations` is run first, and only after it's finished,
     * the `allowSlowOperations` is executed.
     * See comment on [com.intellij.codeInsight.daemon.LineMarkerProvider.collectSlowLineMarkers]
     * for reference being the base for this assumption.
     */
    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        when {
            !allowSlowOperations -> declaration.removeUserData(FAST_COMPUTED_TEST_ICON_KEY)
            // Don't duplicate the icon produced in !allowSlowOperations phase
            allowSlowOperations && declaration.getUserData(FAST_COMPUTED_TEST_ICON_KEY) != null -> return null
        }

        val platform = declaration.module?.platform

        val icons = getInstances()
            .filter { it != this && (platform == null || it.kind.isCompatibleWith(platform)) }
            .mapNotNull { it.getTestIcon(declaration, allowSlowOperations) }
            .distinct()

        val icon = when (icons.size) {
            0 -> null
            else -> icons.singleOrNull() ?: AllIcons.RunConfigurations.TestState.Run
        }
        if (!allowSlowOperations) {
            declaration.putUserData(FAST_COMPUTED_TEST_ICON_KEY, icon)
        }
        return icon
    }

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        val module = function.containingKtFile.module ?: return false
        return module.implementingModules.any { implementingModule ->
            implementingModule.platform.idePlatformKind.takeIf { !it.isCommon }?.tooling?.acceptsAsEntryPoint(function) ?: false
        }
    }
}