// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1.gradle

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.kotlin.gradle.scripting.shared.settings.StandaloneScriptsStorage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.script.AbstractScriptConfigurationLoadingTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

class GradleBuildRootIndexTest : AbstractScriptConfigurationLoadingTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    override fun setUpTestProject() {
        val rootDir = IDEA_TEST_DATA_DIR.resolve("script/definition/loading/gradle/")

        val settings: KtFile = copyFromTestdataToProject(File(rootDir, GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME))
        val prop: PsiFile = copyFromTestdataToProject(File(rootDir, "gradle.properties"))

        val gradleCoreJar = createFileInProject("gradle/lib/gradle-core-1.0.0.jar")
        val gradleWrapperProperties = createFileInProject("gradle/wrapper/gradle-wrapper.properties")

        val buildGradleKts = rootDir.walkTopDown().find { it.name == GradleConstants.KOTLIN_DSL_SCRIPT_NAME }
            ?: error("Couldn't find main script")
        configureScriptFile(rootDir, buildGradleKts)

        val newProjectSettings = GradleProjectSettings()
        newProjectSettings.gradleHome = gradleCoreJar.parentFile.parent
        newProjectSettings.distributionType = DistributionType.LOCAL
        newProjectSettings.externalProjectPath = settings.virtualFile.parent.path

        StandaloneScriptsStorage.getInstance(project)!!.files.add("standalone.kts")

        ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).linkProject(newProjectSettings)
    }

    fun `test standalone scripts on start`() {
        assertNotNull(GradleBuildRootsLocator.getInstance(project))
    }
}