// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

/**
 * A quick fix that adds an opt-in compiler argument to the current module configuration facet.
 */
class AddModuleOptInFix(
    file: KtFile,
    private val module: Module,
    private val annotationFqName: FqName,
) : KotlinQuickFixAction<KtFile>(file) {

    private val configurator by lazy {
        KotlinProjectConfigurator.EP_NAME
            .lazySequence()
            .filter { it.canAddModuleWideOptIn }
            .firstOrNull { it.isApplicable(module) }
    }

    override fun getText(): @IntentionName String = KotlinBundle.message(
        "fix.opt_in.text.use.module",
        annotationFqName.shortName().asString(),
        configurator?.userVisibleNameFor(module) ?: module.name,
    )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.an.opt.in.requirement.marker.compiler.argument")

    /**
     * The actual name of the opt-in compiler argument depends on the Kotlin compiler version:
     * * `-opt-in` (since Kotlin 1.6) https://youtrack.jetbrains.com/issue/KT-47099
     * * `-Xopt-in` (before Kotlin 1.6) https://blog.jetbrains.com/kotlin/2020/03/kotlin-1-3-70-released/
     * * `-Xuse-experimental` (before Kotlin 1.3.70), a fallback if `RequireOptIn` annotation does not exist
     */
    override fun invoke(
        project: Project,
        editor: Editor?,
        file: KtFile,
    ) {
        val optInPrefix = if (KotlinPluginLayout.standaloneCompilerVersion.kotlinVersion.isAtLeast(1, 6, 0)) ""
        else "X"

        configurator?.addModuleWideOptIn(
            module,
            annotationFqName,
            "-${optInPrefix}opt-in=${annotationFqName}",
        )
    }

    override fun isAvailable(
        project: Project,
        editor: Editor?,
        file: KtFile,
    ): Boolean = configurator != null
}