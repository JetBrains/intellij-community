// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.run.multiplatform.KotlinMultiplatformRunLocationsProvider
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.utils.PathUtil
import org.jetbrains.kotlin.utils.ifEmpty

interface KotlinJSRunConfigurationDataProvider<out T : Any> {
    val isForTests: Boolean
    fun getConfigurationData(context: ConfigurationContext): T?
}

abstract class AbstractJsIdePlatformKindTooling : IdePlatformKindTooling() {
    companion object {
        private const val MAVEN_OLD_JS_STDLIB_ID = "kotlin-js-library"
    }

    override val kind = JsIdePlatformKind

    override val mavenLibraryIds = listOf(PathUtil.JS_LIB_NAME, MAVEN_OLD_JS_STDLIB_ID)
    override val gradlePluginId = "kotlin-platform-js"
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.JS)

    override val libraryKind = KotlinJavaScriptLibraryKind

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        val contexts by lazy { computeConfigurationContexts(function) }

        return RunConfigurationProducer
            .getProducers(function.project)
            .asSequence()
            .filterIsInstance<KotlinJSRunConfigurationDataProvider<*>>()
            .filter { !it.isForTests }
            .flatMap { provider -> contexts.map { context -> provider.getConfigurationData(context) } }
            .any { it != null }
    }

    protected fun computeConfigurationContexts(declaration: KtNamedDeclaration): Sequence<ConfigurationContext> {
        val location = PsiLocation(declaration)
        return KotlinMultiplatformRunLocationsProvider().getAlternativeLocations(location).map {
            ConfigurationContext.createEmptyContextForLocation(it)
        }.ifEmpty { listOf(ConfigurationContext.createEmptyContextForLocation(location)) }.asSequence()
    }
}