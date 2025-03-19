// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.replaceService
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.application
import org.jetbrains.kotlin.gradle.GradleDaemonAnalyzerTestCase
import org.jetbrains.kotlin.gradle.checkFiles
import org.jetbrains.kotlin.idea.run.KotlinRunConfiguration
import org.jetbrains.kotlin.idea.test.TagsTestDataUtil
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.awt.Point
import java.io.File
import java.util.*

class GradleTestRunConfigurationAndHighlightingTest23 : KotlinGradleImportingTestCase() {
    @Test
    @TargetVersions("<7.6")
    fun testExpectClassWithTests() {
        enableExperimentalMPP(true)
        doTest()
    }

    @Test
    @TargetVersions("<7.6")
    fun testMultiplatformInheritedTests() {
        mockInheritorPopup()
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
                        return "mainClass=\"${(it.configuration as KotlinRunConfiguration).runClass}\""
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

            result += tag.name.lowercase(Locale.getDefault()) to renderedTagValue
        }

        return result.joinToString { (tagName, tagValue) -> tagName + "=\"" + tagValue.replace("\"", "\\\"") + "\"" }
    }

    private fun LineMarkerInfo<*>.extractConfigurationsFromContext(): List<ConfigurationFromContext> {
        val location = PsiLocation(element)

        // TODO(dsavvinov): consider getting proper context somehow
        val context = ConfigurationContext.createEmptyContextForLocation(location)

        return context.configurationsFromContext.orEmpty()
    }

    private fun mockInheritorPopup() {
        application.replaceService(
            JBPopupFactory::class.java, object : PopupFactoryImpl() {
                override fun <T : Any?> createPopupChooserBuilder(list: MutableList<out T>) = object: PopupChooserBuilder<T>(JBList()) {
                    //Fix project leak in popup test infrastructure. In headless mode popup doesn't set up disposable.
                    //see: com.intellij.ui.popup.AbstractPopup.show(...)
                    override fun createPopup(): JBPopup = super.createPopup().also { popup -> Disposer.dispose(popup) }
                }
                override fun guessBestPopupLocation(dataContext: DataContext) = RelativePoint(Point())
            },
            testRootDisposable
        )
    }

    override fun testDataDirName(): String = "multiplatform/testRunConfigurations"
}
