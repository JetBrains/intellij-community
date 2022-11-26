// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION

private val sections = arrayListOf("buildscript", "plugins", "initscript", "pluginManagement")

fun isGradleKotlinScript(virtualFile: VirtualFile) = virtualFile.name.endsWith(".$KOTLIN_DSL_SCRIPT_EXTENSION")

fun getGradleScriptInputsStamp(
    project: Project,
    file: VirtualFile,
    givenKtFile: KtFile? = null,
    givenTimeStamp: Long = file.modificationStamp
): GradleKotlinScriptConfigurationInputs? {
    if (!isGradleKotlinScript(file)) return null

    return runReadAction {
        val ktFile = givenKtFile ?: PsiManager.getInstance(project).findFile(file) as? KtFile ?: return@runReadAction null

        val result = StringBuilder()
        ktFile.script?.blockExpression
            ?.getChildrenOfType<KtScriptInitializer>()
            ?.forEach {
                val call = it.children.singleOrNull() as? KtCallExpression
                val callRef = call?.firstChild?.text ?: return@forEach
                if (callRef in sections) {
                    result.append(callRef)
                    val lambda = call.lambdaArguments.singleOrNull()
                    lambda?.accept(object : PsiRecursiveElementVisitor(false) {
                        override fun visitElement(element: PsiElement) {
                            super.visitElement(element)
                            when (element) {
                                is PsiWhiteSpace -> if (element.text.contains("\n")) result.append("\n")
                                is PsiComment -> {
                                }
                                is LeafPsiElement -> result.append(element.text)
                            }
                        }
                    })
                    result.append("\n")
                }
            }

        val buildRoot = GradleBuildRootsManager.getInstance(project)?.findScriptBuildRoot(file)?.nearest
        GradleKotlinScriptConfigurationInputs(result.toString(), givenTimeStamp, buildRoot?.pathPrefix)
    }
}

const val minimal_gradle_version_supported = "6.0"

fun kotlinDslScriptsModelImportSupported(currentGradleVersion: String): Boolean {
    return GradleVersion.version(currentGradleVersion) >= GradleVersion.version(minimal_gradle_version_supported)
}

fun getGradleProjectSettings(project: Project): Collection<GradleProjectSettings> =
    (ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID) as GradleSettings).linkedProjectsSettings