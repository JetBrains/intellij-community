// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinSourceRootType
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.idea.facet.KotlinVersionInfoProvider
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.psi.KtFile

/**
 * Increases the language version of [module] to the version of [languageFeature] if possible.
 *
 * The fix doesn't change the Kotlin version of build system plugins, e.g., Kotlin Gradle or Maven plugin versions.
 * This limits the scope of the fix only to the versions already available to the current compiler.
 */
class K2IncreaseLanguageVersionFix(
    element: PsiElement,
    private val module: Module,
    private val languageFeature: LanguageFeature,
) : KotlinQuickFixAction<PsiElement>(element) {
    private val configurator: KotlinProjectConfigurator? by lazy {
        KotlinProjectConfigurator.EP_NAME
            .lazySequence()
            .firstOrNull { it.isApplicable(module) }
    }

    override fun getText(): @IntentionName String {
        val sinceVersion = languageFeature.sinceVersion ?: return familyName
        return KotlinIdeaCoreBundle.message("fix.increase.language.version.text", sinceVersion.versionString)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinIdeaCoreBundle.message("fix.increase.language.version.family", languageFeature.presentableName)

    /**
     * The fix is available under the following conditions:
     * — [languageFeature] has a 'since' version, i.e., the feature is stabilized.
     * — The 'since' version is not greater than the latest stable Kotlin version known to the analyzer.
     * — The 'since' version is not greater than the Kotlin stdlib version in module's dependencies.
     *   Otherwise, increasing the version will break the build.
     */
    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        if (configurator == null) return false
        val featureSinceVersion = languageFeature.sinceVersion ?: return false
        if (featureSinceVersion > LanguageVersion.LATEST_STABLE) return false

        val libraryKotlinVersion = KotlinVersionInfoProvider.EP_NAME.extensionList.flatMap {
            it.getLibraryVersionsSequence(module, module.platform.idePlatformKind, rootModel = null)
        }.singleOrNull()
        return libraryKotlinVersion != null && libraryKotlinVersion.languageVersion >= featureSinceVersion
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val sinceVersion = languageFeature.sinceVersion ?: return
        val sinceVersionString = sinceVersion.versionString
        val facetSettings = KotlinFacetSettingsProvider.getInstance(project)?.getSettings(module)
        val targetApiLevel = facetSettings?.apiLevel?.let { apiLevel ->
            languageFeature.sinceApiVersion.takeIf { ApiVersion.createByLanguageVersion(apiLevel) < languageFeature.sinceApiVersion }
        }
        configurator?.updateLanguageVersion(
            module = module,
            languageVersion = sinceVersionString,
            apiVersion = targetApiLevel?.versionString,
            requiredStdlibVersion = languageFeature.sinceApiVersion,
            forTests = isFileUnderTestRoot(project, file),
        )
    }

    private fun isFileUnderTestRoot(project: Project, file: KtFile): Boolean {
        val virtualFile = file.virtualFile ?: return false
        return ProjectFileIndex.getInstance(project).getKotlinSourceRootType(virtualFile) == TestSourceKotlinRootType
    }
}
