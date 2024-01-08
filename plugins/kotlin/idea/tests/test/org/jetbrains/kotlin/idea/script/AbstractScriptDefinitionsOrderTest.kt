// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.updateScriptDependenciesSynchronously
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.junit.ComparisonFailure

@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
abstract class AbstractScriptDefinitionsOrderTest : AbstractScriptConfigurationTest() {
    fun doTest(unused: String) {
        configureScriptFile(testDataFile())

        val definitions = InTextDirectivesUtils.findStringWithPrefixes(myFile.text, "// SCRIPT DEFINITIONS: ")
            ?.split(";")
            ?.map { it.substringBefore(":").trim() to it.substringAfter(":").trim() }
            ?: error("SCRIPT DEFINITIONS directive should be defined")

        val allDefinitions = ScriptDefinitionsManager.getInstance(project).getAllDefinitions()
        for ((definitionName, action) in definitions) {
            val scriptDefinition = allDefinitions
                .find { it.name == definitionName }
                ?: error("Unknown script definition name in SCRIPT DEFINITIONS directive: name=$definitionName, all={${allDefinitions.joinToString { it.name }}}")
            when (action) {
                "off" -> KotlinScriptingSettings.getInstance(project).setEnabled(scriptDefinition, false)
                else -> KotlinScriptingSettings.getInstance(project).setOrder(scriptDefinition, action.toInt())
            }
        }

        ScriptDefinitionsManager.getInstance(project).reorderScriptDefinitions()
        updateScriptDependenciesSynchronously(myFile)
        checkHighlighting(editor, false, false)
    }
}