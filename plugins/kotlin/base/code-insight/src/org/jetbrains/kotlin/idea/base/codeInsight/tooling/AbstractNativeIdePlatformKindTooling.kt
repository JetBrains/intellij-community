// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.idea.base.facet.externalSystemNativeMainRunTasks
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.base.platforms.KotlinNativeLibraryKind
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction

interface KotlinNativeRunConfigurationProvider {
    val isForTests: Boolean
}

abstract class AbstractNativeIdePlatformKindTooling : IdePlatformKindTooling() {
    override val kind: NativeIdePlatformKind = NativeIdePlatformKind

    override val mavenLibraryIds: List<String> get() = emptyList()
    override val gradlePluginId: String get() = ""
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.NATIVE)

    override val libraryKind: PersistentLibraryKind<*> get() = KotlinNativeLibraryKind

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        val functionName = function.fqName?.asString() ?: return false

        val module = function.module ?: return false
        if (module.isTestModule) return false

        val hasRunTask = module.externalSystemNativeMainRunTasks().any { it.entryPoint == functionName }
        if (!hasRunTask) return false

        val hasRunConfigurations = RunConfigurationProducer
            .getProducers(function.project)
            .asSequence()
            .filterIsInstance<KotlinNativeRunConfigurationProvider>()
            .any { !it.isForTests }

        return hasRunConfigurations
    }
}