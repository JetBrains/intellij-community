// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.extensions.ApplicationExtensionDescriptor
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

abstract class IdePlatformKindTooling {
    abstract val kind: IdePlatformKind

    abstract val mavenLibraryIds: List<String>
    abstract val gradlePluginId: String

    abstract val gradlePlatformIds: List<KotlinPlatform>

    abstract val libraryKind: PersistentLibraryKind<*>?

    abstract val testIconProvider: AbstractGenericTestIconProvider

    abstract fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon?

    abstract fun acceptsAsEntryPoint(function: KtFunction): Boolean

    override fun equals(other: Any?): Boolean = javaClass == other?.javaClass
    override fun hashCode(): Int = javaClass.hashCode()

    companion object : ApplicationExtensionDescriptor<IdePlatformKindTooling>(
        "org.jetbrains.kotlin.idePlatformKindTooling", IdePlatformKindTooling::class.java
    ) {
        private val ALL_TOOLING_SUPPORT: List<IdePlatformKindTooling> by lazy { getInstances() }

        private val TOOLING_SUPPORT_BY_KIND: Map<IdePlatformKind, IdePlatformKindTooling> by lazy {
            val allPlatformKinds = IdePlatformKind.ALL_KINDS
            val groupedTooling = ALL_TOOLING_SUPPORT.groupBy { it.kind }.mapValues { it.value.single() }

            for (kind in allPlatformKinds) {
                if (kind !in groupedTooling) {
                    throw IllegalStateException(
                        "Tooling support for the platform '$kind' is missing. " +
                                "Implement 'IdePlatformKindTooling' for it."
                    )
                }
            }

            groupedTooling
        }

        private val TOOLING_SUPPORT_BY_PLATFORM_ID: Map<KotlinPlatform, IdePlatformKindTooling> by lazy {
            ALL_TOOLING_SUPPORT.flatMap { tooling -> tooling.gradlePlatformIds.map { it to tooling } }.toMap()
        }

        fun getTooling(kind: IdePlatformKind): IdePlatformKindTooling {
            return TOOLING_SUPPORT_BY_KIND[kind] ?: error("Unknown platform $kind")
        }

        /**
         * @return null if current IDE doesn't know given platform
         */
        fun getToolingIfAny(platformId: KotlinPlatform): IdePlatformKindTooling? {
            return TOOLING_SUPPORT_BY_PLATFORM_ID[platformId]
        }

        fun getTooling(platformId: KotlinPlatform): IdePlatformKindTooling {
            return getToolingIfAny(platformId) ?: error("Unknown Gradle platform $platformId")
        }
    }
}

val KotlinPlatform.tooling: IdePlatformKindTooling
    get() = IdePlatformKindTooling.getTooling(this)

val IdePlatformKind.tooling: IdePlatformKindTooling
    get() = IdePlatformKindTooling.getTooling(this)