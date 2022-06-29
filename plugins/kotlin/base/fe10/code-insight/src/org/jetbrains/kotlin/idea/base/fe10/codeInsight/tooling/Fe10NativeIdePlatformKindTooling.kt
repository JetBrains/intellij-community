// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractNativeIdePlatformKindTooling
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.KotlinNativeRunConfigurationProvider
import org.jetbrains.kotlin.idea.base.facet.externalSystemNativeMainRunTasks
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.base.platforms.KotlinNativeLibraryKind
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor.Companion.getTestStateIcon
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import javax.swing.Icon

class Fe10NativeIdePlatformKindTooling : AbstractNativeIdePlatformKindTooling() {
    override val kind = NativeIdePlatformKind

    override val mavenLibraryIds: List<String> get() = emptyList()
    override val gradlePluginId: String get() = ""
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.NATIVE)

    override val libraryKind: PersistentLibraryKind<*> = KotlinNativeLibraryKind

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        if (!function.isMainFunction()) return false
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
        if (!hasRunConfigurations) return false

        return true
    }

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        if (!allowSlowOperations) return null
        val descriptor = declaration.resolveToDescriptorIfAny() ?: return null
        if (!descriptor.isKotlinTestDeclaration()) return null

        val moduleName = descriptor.module.stableName?.asString() ?: ""
        val targetName = moduleName.substringAfterLast(".").removeSuffix("Test>")

        val urls = when (declaration) {
            is KtClassOrObject -> {
                val lightClass = declaration.toLightClass() ?: return null
                listOf("java:suite://${lightClass.qualifiedName}")
            }

            is KtNamedFunction -> {
                val lightMethod = declaration.toLightMethods().firstOrNull() ?: return null
                val lightClass = lightMethod.containingClass as? KtLightClass ?: return null
                val baseName = "java:test://${lightClass.qualifiedName}.${lightMethod.name}"
                listOf("$baseName[${targetName}X64]", "$baseName[$targetName]", baseName)
            }

            else -> return null
        }

        return getTestStateIcon(urls, declaration)
    }
}

@ApiStatus.Internal
fun KtElement.isMainFunction(computedDescriptor: DeclarationDescriptor? = null): Boolean {
    if (this !is KtNamedFunction) return false
    val mainFunctionDetector = MainFunctionDetector(this.languageVersionSettings) { it.resolveToDescriptorIfAny() }

    if (computedDescriptor != null) return mainFunctionDetector.isMain(computedDescriptor)

    return mainFunctionDetector.isMain(this)
}