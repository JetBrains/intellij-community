// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.facet.getOrCreateConfiguredFacet
import org.jetbrains.kotlin.idea.quickfix.ExperimentalFixesFactory.fqNameIsExisting
import org.jetbrains.kotlin.idea.roots.invalidateProjectRoots
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.idea.versions.kotlinCompilerVersionShort
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.checkWithAttachment

/**
 * A quick fix that adds an opt-in compiler argument to the current module configuration facet (JPS only).
 */
open class MakeModuleExperimentalFix(
    file: KtFile,
    private val module: Module,
    annotationFqName: FqName
) : KotlinQuickFixAction<KtFile>(file) {

    // The actual name of the opt-in compiler argument depends on the Kotlin compiler version
    // `-opt-in` (since Kotlin 1.6) https://youtrack.jetbrains.com/issue/KT-47099
    // `-Xopt-in` (before Kotlin 1.6) https://blog.jetbrains.com/kotlin/2020/03/kotlin-1-3-70-released/
    // `-Xuse-experimental` (before Kotlin 1.3.70), a fallback if `RequireOptIn` annotation does not exist

    private val kotlinCompilerVersion = KotlinVersion.fromString(kotlinCompilerVersionShort())

    private val experimentalPrefix = when {
        module.toDescriptor()?.fqNameIsExisting(OptInNames.REQUIRES_OPT_IN_FQ_NAME) == false -> "-Xuse-experimental"
        kotlinCompilerVersion.isAtLeast(1, 6, 0) -> "-opt-in"
        else -> "-Xopt-in"
    }

    private val compilerArgument = "$experimentalPrefix=$annotationFqName"

    override fun getText(): String = KotlinBundle.message("add.0.to.module.1.compiler.arguments", compilerArgument, module.name)

    override fun getFamilyName(): String = KotlinBundle.message("add.an.opt.in.requirement.marker.compiler.argument")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
        try {
            module.getOrCreateConfiguredFacet(modelsProvider, useProjectSettings = false, commitModel = true) {
                val facetSettings = configuration.settings
                val compilerSettings = facetSettings.compilerSettings ?: CompilerSettings().also {
                    facetSettings.compilerSettings = it
                }

                compilerSettings.additionalArguments += " $compilerArgument"
                facetSettings.updateMergedArguments()
            }
            project.invalidateProjectRoots()
        } finally {
            modelsProvider.dispose()
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        // This fix can be used for JPS only as it changes facet settings,
        // and Gradle and Maven facets are reset when the project is reloaded.
        return module.getBuildSystemType() == BuildSystemType.JPS
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val containingKtFile = diagnostic.psiElement.containingFile as? KtFile ?: return null
            val module = containingKtFile.module ?: return null
            return MakeModuleExperimentalFix(
                containingKtFile,
                module,
                OptInNames.REQUIRES_OPT_IN_FQ_NAME.takeIf {
                    module.toDescriptor()?.fqNameIsExisting(it) == true
                } ?: OptInNames.OLD_EXPERIMENTAL_FQ_NAME
            )
        }
    }
}

private fun KotlinVersion.Companion.fromString(version: String): KotlinVersion {
    val parts = version.split(".")
    require(parts.size == 3)
    return KotlinVersion(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}
