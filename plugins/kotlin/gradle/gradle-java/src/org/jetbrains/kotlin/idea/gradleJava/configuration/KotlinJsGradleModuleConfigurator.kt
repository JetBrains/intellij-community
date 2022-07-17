// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.*
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
    override fun getStdlibArtifactName(sdk: Sdk?, version: IdeKotlinVersion): String = MAVEN_JS_STDLIB_ID

    override fun addElementsToFile(file: PsiFile, isTopLevelProjectFile: Boolean, version: IdeKotlinVersion): Boolean {
        val gradleVersion = GradleVersionProvider.fetchGradleVersion(file)

        if (GradleBuildScriptSupport.getManipulator(file).useNewSyntax(kotlinPluginName, gradleVersion)) {
            val settingsPsiFile = if (isTopLevelProjectFile) {
                file.module?.getTopLevelBuildScriptSettingsPsiFile()
            } else {
                file.module?.getBuildScriptSettingsPsiFile()
            }
            if (settingsPsiFile != null) {
                GradleBuildScriptSupport.getManipulator(settingsPsiFile).addResolutionStrategy(KOTLIN_JS)
            }
        }

        return super.addElementsToFile(file, isTopLevelProjectFile, version)
    }

    companion object {
        @NonNls
        const val KOTLIN_JS = "kotlin2js"
    }
}
