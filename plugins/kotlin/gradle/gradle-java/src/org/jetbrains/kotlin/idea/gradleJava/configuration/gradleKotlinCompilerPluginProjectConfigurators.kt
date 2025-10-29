// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion.Companion.defaultKotlinVersion
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptPsiFile
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.PathUtil

abstract class AbstractGradleKotlinCompilerPluginProjectConfigurator(private val coroutineScope: CoroutineScope): KotlinCompilerPluginProjectConfigurator {
    override fun configureModule(module: Module): PsiFile? {
        val project = module.project
        val changedFiles = ChangedConfiguratorFiles()
        val file = project.getTopLevelBuildScriptPsiFile() ?: return null

        coroutineScope.launchTracked {
            edtWriteAction {
                project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", file.name), null) {
                    val manipulator = GradleBuildScriptSupport.getManipulator(file)
                    val version =
                        manipulator.getKotlinVersionFromBuildScript() ?: defaultKotlinVersion
                    manipulator.configureBuildScripts(
                        kotlinPluginName,
                        getKotlinPluginExpression(file is KtFile),
                        PathUtil.KOTLIN_JAVA_STDLIB_NAME,
                        addVersion = true,
                        version = version,
                        jvmTarget = null,
                        changedFiles = changedFiles
                    )
                }
            }
        }
        return file
    }

    protected abstract val kotlinPluginName: String

    protected abstract fun getKotlinPluginExpression(forKotlinDsl: Boolean): String
}

class SpringGradleKotlinCompilerPluginProjectConfigurator(coroutineScope: CoroutineScope): AbstractGradleKotlinCompilerPluginProjectConfigurator(coroutineScope) {
    override val kotlinPluginName: String
        get() = "kotlin.spring"

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "kotlin(\"plugin.spring\")" else "id \"org.jetbrains.kotlin.plugin.spring\""

    override val compilerId: String = "kotlin-spring"

}