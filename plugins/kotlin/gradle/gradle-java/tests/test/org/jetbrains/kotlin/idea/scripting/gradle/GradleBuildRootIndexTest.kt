// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.scripting.gradle

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.script.AbstractScriptConfigurationLoadingTest
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.idea.gradle.scripting.settings.StandaloneScriptsStorage
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

class GradleBuildRootIndexTest : AbstractScriptConfigurationLoadingTest() {
    override fun setUpTestProject() {
        val rootDir = IDEA_TEST_DATA_DIR.resolve("script/definition/loading/gradle/")

        val settings: KtFile = copyFromTestdataToProject(File(rootDir, GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME))
        val prop: PsiFile = copyFromTestdataToProject(File(rootDir, "gradle.properties"))

        val gradleCoreJar = createFileInProject("gradle/lib/gradle-core-1.0.0.jar")
        val gradleWrapperProperties = createFileInProject("gradle/wrapper/gradle-wrapper.properties")

        val buildGradleKts = rootDir.walkTopDown().find { it.name == GradleConstants.KOTLIN_DSL_SCRIPT_NAME }
            ?: error("Couldn't find main script")
        configureScriptFile(rootDir, buildGradleKts)
        val build = (myFile as? KtFile) ?: error("")

        val newProjectSettings = GradleProjectSettings()
        newProjectSettings.gradleHome = gradleCoreJar.parentFile.parent
        newProjectSettings.distributionType = DistributionType.LOCAL
        newProjectSettings.externalProjectPath = settings.virtualFile.parent.path

        StandaloneScriptsStorage.getInstance(project)!!.files.add("standalone.kts")

        ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).linkProject(newProjectSettings)
    }

    fun `test standalone scripts on start`() {
        assertNotNull(GradleBuildRootsManager.getInstance(project))
    }
}