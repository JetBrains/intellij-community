// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.allConfigurators
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.checkers.OptInNames

/**
 * A quick fix that adds an opt-in compiler argument to the current module configuration facet.
 */
open class MakeModuleOptInFix(
    file: KtFile,
    private val module: Module,
    private val annotationFqName: FqName,
) : KotlinQuickFixAction<KtFile>(file) {

    private val configurator by lazy {
        allConfigurators().find { it.isApplicable(module) && it.canAddModuleWideOptIn }
    }

    /**
     * The actual name of the opt-in compiler argument depends on the Kotlin compiler version:
     * * `-opt-in` (since Kotlin 1.6) https://youtrack.jetbrains.com/issue/KT-47099
     * * `-Xopt-in` (before Kotlin 1.6) https://blog.jetbrains.com/kotlin/2020/03/kotlin-1-3-70-released/
     * * `-Xuse-experimental` (before Kotlin 1.3.70), a fallback if `RequireOptIn` annotation does not exist
     */
    private val compilerArgName = when {
        module.toDescriptor()?.let { OptInFixesUtils.annotationExists(it, OptInNames.REQUIRES_OPT_IN_FQ_NAME) } == false -> "-Xuse-experimental"
        KotlinPluginLayout.standaloneCompilerVersion.kotlinVersion.isAtLeast(1, 6, 0) -> "-opt-in"
        else -> "-Xopt-in"
    }

    private val compilerArgument = "$compilerArgName=$annotationFqName"

    override fun getText(): String = KotlinBundle.message(
        "fix.opt_in.text.use.module",
        annotationFqName.shortName().asString(),
        configurator?.userVisibleNameFor(module) ?: module.name
    )

    override fun getFamilyName(): String = KotlinBundle.message("add.an.opt.in.requirement.marker.compiler.argument")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        configurator?.addModuleWideOptIn(module, annotationFqName, compilerArgument)
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = configurator != null

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val containingKtFile = diagnostic.psiElement.containingFile as? KtFile ?: return null
            val module = containingKtFile.module ?: return null
            val moduleDescriptor = module.toDescriptor()
            return MakeModuleOptInFix(
                containingKtFile,
                module,
                OptInNames.REQUIRES_OPT_IN_FQ_NAME.takeIf {
                    moduleDescriptor != null && OptInFixesUtils.annotationExists(moduleDescriptor, it)
                } ?: FqNames.OptInFqNames.OLD_EXPERIMENTAL_FQ_NAME
            )
        }
    }
}
