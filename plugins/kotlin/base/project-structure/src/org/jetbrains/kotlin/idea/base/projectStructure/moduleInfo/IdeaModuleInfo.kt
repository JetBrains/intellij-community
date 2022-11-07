// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.platform.TargetPlatformVersion

val OriginCapability = ModuleCapability<ModuleOrigin>("MODULE_ORIGIN")

enum class ModuleOrigin {
    MODULE,
    LIBRARY,
    OTHER
}

interface IdeaModuleInfo : ModuleInfo {
    val contentScope: GlobalSearchScope

    val moduleContentScope: GlobalSearchScope
        get() = contentScope

    val moduleOrigin: ModuleOrigin

    val project: Project?

    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() = super.capabilities + mapOf(OriginCapability to moduleOrigin)

    override fun dependencies(): List<IdeaModuleInfo>
    fun dependenciesWithoutSelf(): Sequence<IdeaModuleInfo> = dependencies().asSequence().filter { it != this }

    fun checkValidity() {}
}

interface LanguageSettingsOwner {
    val languageVersionSettings: LanguageVersionSettings
    val targetPlatformVersion: TargetPlatformVersion
}

fun Project.ideaModules(): Array<out Module> = runReadAction { ModuleManager.getInstance(this).modules }