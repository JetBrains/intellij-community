// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.extensions.gradle.*
import org.jetbrains.kotlin.idea.gradle.KotlinGradleFacadeImpl
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.idea.versions.MAVEN_JS_STDLIB_ID
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms

class KotlinJsGradleModuleConfigurator : KotlinWithGradleConfigurator() {
    override val name: String = "gradle-js"
    override val presentableText: String get() = KotlinIdeaGradleBundle.message("presentable.text.javascript.with.gradle")
    override val targetPlatform: TargetPlatform = JsPlatforms.defaultJsPlatform
    override val kotlinPluginName: String = KOTLIN_JS
    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "id(\"kotlin2js\")" else "id 'kotlin2js'"

    override fun getMinimumSupportedVersion() = "1.1.0"
    override fun getStdlibArtifactName(sdk: Sdk?, version: String): String = MAVEN_JS_STDLIB_ID

    @Suppress("DEPRECATION_ERROR")
    override fun getTargetPlatform() = JsPlatforms.CompatJsPlatform

    override fun addElementsToFile(file: PsiFile, isTopLevelProjectFile: Boolean, version: String): Boolean {
        val gradleVersion = GradleVersionProviderImpl.fetchGradleVersion(file)

        if (KotlinGradleFacadeImpl.getManipulator(file).useNewSyntax(kotlinPluginName, gradleVersion, GradleVersionProviderImpl)) {
            val settingsPsiFile = if (isTopLevelProjectFile) {
                file.module?.getTopLevelBuildScriptSettingsPsiFile()
            } else {
                file.module?.getBuildScriptSettingsPsiFile()
            }
            if (settingsPsiFile != null) {
                KotlinGradleFacadeImpl.getManipulator(settingsPsiFile).addResolutionStrategy(KOTLIN_JS)
            }
        }

        return super.addElementsToFile(file, isTopLevelProjectFile, version)
    }

    companion object {
        @NonNls
        const val KOTLIN_JS = "kotlin2js"
    }
}
