// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.core.isAndroidModule
import org.jetbrains.kotlin.idea.versions.getDefaultJvmTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class KotlinGradleModuleConfigurator : KotlinWithGradleConfigurator() {

    override val name: String
        get() = NAME

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    @Suppress("DEPRECATION_ERROR")
    override fun getTargetPlatform() = JvmPlatforms.CompatJvmPlatform

    override val presentableText: String
        get() = KotlinIdeaGradleBundle.message("presentable.text.java.with.gradle")

    override val kotlinPluginName: String
        get() = KOTLIN

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "kotlin(\"jvm\")" else "id 'org.jetbrains.kotlin.jvm'"

    override fun getJvmTarget(sdk: Sdk?, version: String) = getDefaultJvmTarget(sdk, version)?.description

    override fun isApplicable(module: Module): Boolean {
        return super.isApplicable(module) && !module.isAndroidModule()
    }

    override fun configureModule(
        module: Module,
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        version: String, collector: NotificationMessageCollector,
        filesToOpen: MutableCollection<PsiFile>
    ) {
        super.configureModule(module, file, isTopLevelProjectFile, version, collector, filesToOpen)

        val moduleGroup = module.getWholeModuleGroup()
        for (sourceModule in moduleGroup.allModules()) {
            if (addStdlibToJavaModuleInfo(sourceModule, collector)) {
                break
            }
        }
    }

    companion object {
        @NonNls
        const val NAME = "gradle"

        @NonNls
        const val KOTLIN = "kotlin"
    }
}
