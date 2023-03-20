// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.gradle.GradleDaemonAnalyzerTestCase
import org.jetbrains.kotlin.gradle.checkFiles
import org.jetbrains.kotlin.idea.run.KotlinRunConfiguration
import org.jetbrains.kotlin.idea.test.TagsTestDataUtil
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Test
import java.io.File

class GradleTestRunConfigurationAndHighlightingTest23 : KotlinGradleImportingTestCase() {
    @Test
    fun testExpectClassWithTests() {
        enableExperimentalMPP(true)
        doTest()
    }

    @Test
    fun preferredConfigurations() = doTest()

    @Test
    fun multiplatformTestsInObject() = doTest()

    @Test
    fun testMultiProjectBuild() = doTest()

    @Test
    fun kotlinJUnitSettings() = doTest()


    protected fun doTest() {
        val files = importProjectFromTestData()
        val project = myTestFixture.project

        checkFiles(
            files.filter { it.extension == "kt" },
            project,
            object : GradleDaemonAnalyzerTestCase(
                testLineMarkers = true,
                checkWarnings = true,
                checkInfos = false,
                rootDisposable = testRootDisposable
            ) {
                override fun renderAdditionalAttributeForTag(tag: TagsTestDataUtil.TagInfo<*>): String? {
                    val lineMarkerInfo = tag.data as? LineMarkerInfo<*> ?: return null

                    // Hacky way to check if it's test line-marker info. Can't rely on extractConfigurationsFromContext returning no
                    // suitable configurationsFromContext, because it basically works on offsets, so if for some range we have two
                    // line markers - one with tests, and one without, - then we'll get proper ConfigurationFromContext for both

                    val extractConfigurationsFromContext = lineMarkerInfo.extractConfigurationsFromContext()
                    val kotlinRunConfigsFromContext = extractConfigurationsFromContext
                        .filter { it.configuration is KotlinRunConfiguration }
                    kotlinRunConfigsFromContext.singleOrNull()?.let {
                        return "mainClass=\"${it.configuration.cast<KotlinRunConfiguration>().runClass}\""
                    }

                    if ("Run Test" !in lineMarkerInfo.lineMarkerTooltip.orEmpty()) return null

                    val kotlinGradleConfigsFromContext = extractConfigurationsFromContext
                        .filter { it.configuration is GradleRunConfiguration }
                    if (kotlinRunConfigsFromContext.isNotEmpty()) return "settings=\"Nothing here\""

                    if (kotlinGradleConfigsFromContext.isEmpty()) return "settings=\"Nothing here\""

                    val configFromContext = kotlinGradleConfigsFromContext.single() // can we have more than one?

                    val tagsToRender = RunConfigurationsTags.getTagsToRender(lineMarkerInfo.element!!.containingFile)

                    return configFromContext.renderDescription(tagsToRender)
                }
            }
        )
    }

    private enum class RunConfigurationsTags {
        PROJECT, SETTINGS;

        companion object {
            const val TAG_DIRECTIVE: String = "// !RENDER_TAGS: "

            val DEFAULT_TAGS = listOf(SETTINGS)

            fun getTagsToRender(file: PsiFile): List<RunConfigurationsTags> {
                val tagDirectives = file.text.lines().filter { it.startsWith(TAG_DIRECTIVE) }

                return if (tagDirectives.isEmpty()) DEFAULT_TAGS else tagDirectives.single().parseTags()
            }

            // Expected format:
            // !RENDER_TAGS: ENUM_VALUE_1, ENUM_VALUE_2, ...
            private fun String.parseTags(): List<RunConfigurationsTags> =
                removePrefix(TAG_DIRECTIVE).split(", ").map { valueOf(it) }
        }
    }

    private fun ConfigurationFromContext.renderDescription(tagsToRender: List<RunConfigurationsTags>): String {
        val configuration = configuration as GradleRunConfiguration

        val location = PsiLocation(sourceElement)
        val context = ConfigurationContext.createEmptyContextForLocation(location)

        var settings: ExternalSystemTaskExecutionSettings? = null

        // We can not use settings straight away, because exact settings are determined only after 'onFirstRun'
        // (see MultiplatformTestTasksChooser)
        onFirstRun(context) {
            settings = configuration.settings
        }

        val result = mutableListOf<Pair<String, String>>()

        for (tag in tagsToRender) {
            val renderedTagValue = when (tag) {
                RunConfigurationsTags.PROJECT -> {
                    val currentProjectFile = File(settings!!.externalProjectPath)
                    val projectRoot = File(ExternalSystemApiUtil.getExternalRootProjectPath(context.module)!!)

                    val pathToCurrentProjectRelativeToRoot = currentProjectFile.relativeTo(projectRoot)
                    pathToCurrentProjectRelativeToRoot.toString()
                }
                RunConfigurationsTags.SETTINGS -> settings.toString()
            }

            result += tag.name.toLowerCase() to renderedTagValue
        }

        return result.joinToString { (tagName, tagValue) -> tagName + "=\"" + tagValue.replace("\"", "\\\"") + "\"" }
    }

    private fun LineMarkerInfo<*>.extractConfigurationsFromContext(): List<ConfigurationFromContext> {
        val location = PsiLocation(element)

        // TODO(dsavvinov): consider getting proper context somehow
        val context = ConfigurationContext.createEmptyContextForLocation(location)

        return context.configurationsFromContext.orEmpty()
    }

    override fun testDataDirName(): String = "testRunConfigurations"
}
