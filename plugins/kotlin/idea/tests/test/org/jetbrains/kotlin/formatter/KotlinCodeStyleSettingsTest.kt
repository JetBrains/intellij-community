// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.codeStyle.json.CodeStyleSchemeJsonExporter
import com.intellij.testFramework.LightPlatformTestCase
import org.jdom.Element
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntry
import org.jetbrains.kotlin.idea.formatter.*
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.ByteArrayOutputStream
import java.io.File


class KotlinCodeStyleSettingsTest : LightPlatformTestCase() {
    fun `test json export with official code style`() = doTestWithJson(KotlinStyleGuideCodeStyle.INSTANCE, "officialCodeStyle")
    fun `test json export with obsolete code style`() = doTestWithJson(KotlinObsoleteCodeStyle.INSTANCE, "obsoleteCodeStyle")
    fun `test compare code styles`() = compareCodeStyle { KotlinStyleGuideCodeStyle.apply(it) }
    fun `test compare different import`() = compareCodeStyle {
        it.kotlinCustomSettings.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry("not.my.package.name", true))
        it.kotlinCommonSettings.BRACE_STYLE = 10
    }

    fun `test compare different layout`() = compareCodeStyle {
        it.kotlinCustomSettings.PACKAGES_IMPORT_LAYOUT.addEntry(KotlinPackageEntry("my.package.name", true))
        it.kotlinCommonSettings.BRACE_STYLE = 10
    }

    fun `test official code style scheme`() = doTestWithScheme(KotlinStyleGuideCodeStyle.INSTANCE, "officialCodeStyleScheme.xml")
    fun `test obsolete code style scheme`() = doTestWithScheme(KotlinObsoleteCodeStyle.INSTANCE, "obsoleteCodeStyleScheme.xml")

    private fun doTestWithScheme(codeStyle: KotlinPredefinedCodeStyle, fileName: String) {
        doWithTemporarySettings {
            ProjectCodeStyleImporter.apply(project, codeStyle)
            val optionElement = Element("code_scheme")
            optionElement.setAttribute("name", "Project")
            CodeStyle.getSettings(project).writeExternal(optionElement)
            KotlinTestUtils.assertEqualsToFile(getTestFile(fileName), JDOMUtil.writeElement(optionElement))
        }
    }

    private fun doWithTemporarySettings(action: (CodeStyleSettings) -> Unit) {
        val settingsManager = CodeStyleSettingsManager.getInstance(project)
        val tempSettingsBefore = settingsManager.temporarySettings
        try {
            val tempSettings = settingsManager.createTemporarySettings()
            settingsManager.dropTemporarySettings()
            CodeStyle.setMainProjectSettings(project, tempSettings)
            action(tempSettings)
        } finally {
            if (tempSettingsBefore != null) {
                settingsManager.setTemporarySettings(tempSettingsBefore)
            }

            settingsManager.USE_PER_PROJECT_SETTINGS = false
            settingsManager.mainProjectCodeStyle = null
        }
    }

    private fun compareCodeStyle(transformer: (CodeStyleSettings) -> Unit) {
        val settings = CodeStyle.getSettings(project)
        val copyOfSettings = CodeStyleSettingsManager.getInstance().cloneSettings(settings)

        assertTrue(settings.kotlinCommonSettings == settings.kotlinCommonSettings)
        assertTrue(settings.kotlinCustomSettings == settings.kotlinCustomSettings)
        assertTrue(copyOfSettings.kotlinCommonSettings == copyOfSettings.kotlinCommonSettings)
        assertTrue(copyOfSettings.kotlinCustomSettings == copyOfSettings.kotlinCustomSettings)
        assertTrue(settings.kotlinCommonSettings == copyOfSettings.kotlinCommonSettings)
        assertTrue(copyOfSettings.kotlinCustomSettings == settings.kotlinCustomSettings)

        transformer(copyOfSettings)

        assertFalse(settings.kotlinCommonSettings == copyOfSettings.kotlinCommonSettings)
        assertFalse(settings.kotlinCustomSettings == copyOfSettings.kotlinCustomSettings)

        assertTrue(copyOfSettings.kotlinCommonSettings == copyOfSettings.kotlinCommonSettings)
        assertTrue(copyOfSettings.kotlinCustomSettings == copyOfSettings.kotlinCustomSettings)
    }
}

private fun doTestWithJson(codeStyle: KotlinPredefinedCodeStyle, fileName: String) {
    val jsonScheme = getTestFile("$fileName.json")

    val testScheme = createTestScheme()
    val settings = testScheme.codeStyleSettings
    codeStyle.apply(settings)

    val kotlinCustomCodeStyle = settings.kotlinCustomSettings
    kotlinCustomCodeStyle.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry("java.util", false))
    kotlinCustomCodeStyle.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry("kotlinx.android.synthetic", true))
    kotlinCustomCodeStyle.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry("io.ktor", true))

    val exporter = CodeStyleSchemeJsonExporter()
    val outputStream = ByteArrayOutputStream()
    exporter.exportScheme(testScheme, outputStream, listOf("kotlin"))
    KotlinTestUtils.assertEqualsToFile(jsonScheme, outputStream.toString())
}

private fun getTestFile(fileName: String): File = File(IDEA_TEST_DATA_DIR, "codeStyle/$fileName").also { assert(it.exists()) }

private fun createTestScheme() = object : CodeStyleScheme {
    private val mySettings = CodeStyle.createTestSettings()
    override fun getName(): String = "Test"

    override fun isDefault(): Boolean = false

    override fun getCodeStyleSettings(): CodeStyleSettings = mySettings
}