// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.platform.TargetPlatformVersion
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

@K1ModeProjectStructureApi
val OriginCapability = ModuleCapability<ModuleOrigin>("MODULE_ORIGIN")

@K1ModeProjectStructureApi
enum class ModuleOrigin {
    MODULE,
    LIBRARY,
    OTHER
}

@K1ModeProjectStructureApi
interface IdeaModuleInfo : ModuleInfo {
    val contentScope: GlobalSearchScope

    val moduleContentScope: GlobalSearchScope
        get() = contentScope

    val moduleOrigin: ModuleOrigin

    val project: Project

    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() = super.capabilities + mapOf(OriginCapability to moduleOrigin)

    override fun dependencies(): List<IdeaModuleInfo>

    fun sdk(): Sdk? = dependencies().firstIsInstanceOrNull<SdkInfo>()?.sdk

    fun dependenciesWithoutSelf(): Sequence<IdeaModuleInfo> = dependencies().asSequence().filter { it != this }

    fun checkValidity() {}
}

interface LanguageSettingsOwner {
    val languageVersionSettings: LanguageVersionSettings
    val targetPlatformVersion: TargetPlatformVersion
}

fun Project.ideaModules(): Array<out Module> = runReadAction { ModuleManager.getInstance(this).modules }

fun Collection<IdeaModuleInfo>.checkValidity(lazyMessage: () -> String) {
    val disposed = filter {
        when (it) {
            is ModuleSourceInfo -> it.module.isDisposed
            is LibraryInfo -> it.library.isDisposed
            else -> false
        }
    }
    if (disposed.isNotEmpty()) {
        throw KotlinExceptionWithAttachments(lazyMessage())
            .withAttachment("disposedInfos.txt", disposed.joinToString("\n") { it.name.asString() })
    }
}