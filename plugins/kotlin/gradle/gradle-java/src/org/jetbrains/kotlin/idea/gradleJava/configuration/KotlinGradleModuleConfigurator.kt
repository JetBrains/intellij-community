// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.projectStructure.allModules
import org.jetbrains.kotlin.idea.base.projectStructure.getWholeModuleGroup
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.configuration.addStdlibToJavaModuleInfo
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.projectConfiguration.getDefaultJvmTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class KotlinGradleModuleConfigurator : KotlinWithGradleConfigurator() {

    override val name: String
        get() = NAME

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    override val presentableText: String
        get() = KotlinIdeaGradleBundle.message("presentable.text.java.with.gradle")

    override val kotlinPluginName: String
        get() = KOTLIN

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "kotlin(\"jvm\")" else "id 'org.jetbrains.kotlin.jvm'"

    override fun getJvmTarget(sdk: Sdk?, version: IdeKotlinVersion): String? = getDefaultJvmTarget(sdk, version)?.description

    override fun isApplicable(module: Module): Boolean {
        return super.isApplicable(module) && !module.isAndroidModule()
    }

    override fun canRunAutoConfig(): Boolean = isAutoConfigurationEnabled()

    override fun configureModule(
        module: Module,
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        ideKotlinVersion: IdeKotlinVersion,
        jvmTarget: String?,
        collector: NotificationMessageCollector,
        changedFiles: ChangedConfiguratorFiles,
        addVersion: Boolean
    ): Boolean {
        val configured =
            super.configureModule(module, file, isTopLevelProjectFile, ideKotlinVersion, jvmTarget, collector, changedFiles, addVersion)

        val moduleGroup = module.getWholeModuleGroup()
        for (sourceModule in moduleGroup.allModules()) {
            if (addStdlibToJavaModuleInfo(sourceModule, collector)) {
                break
            }
        }
        return configured
    }

    companion object {
        @NonNls
        const val NAME: String = "gradle"

        @NonNls
        const val KOTLIN: String = "kotlin"
    }
}
