// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.k1.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File
import kotlin.script.dependencies.Environment


private val scripDefinitionCompilationClassPath = listOf(
    TestKotlinArtifacts.kotlinScriptRuntime,
    TestKotlinArtifacts.kotlinScriptingCommon,
    TestKotlinArtifacts.kotlinScriptingJvm,

    )

internal fun prepareScriptDefinitions(
    project: Project,
    testName: String,
    scriptDefinitionSourcePath: String,
    testRootDisposable: Disposable,
    aKtsLibs: List<File>,
    bKtsLibs: List<File>
): TestEnvironment {

    compileLibToDir(testName, File(scriptDefinitionSourcePath))
    return TestEnvironment(
        mutableMapOf(
            "lib-classes-A" to aKtsLibs,
            "lib-classes-B" to bKtsLibs,
            "template-classes-names" to listOf(
                "org.jetbrains.kotlin.idea.script.definition.TestScriptDefinitionA",
                "org.jetbrains.kotlin.idea.script.definition.TestScriptDefinitionB"
            )
        )
    ).also {
        registerScriptDefinitionsProvider(project, testRootDisposable, it.env)
    }
}


private fun compileLibToDir(testName: String, srcDir: File): File {
    val outDir = KotlinTestUtils.tmpDirForReusableFolder("${testName}${srcDir.name}Out")
    KotlinCompilerStandalone(
        listOf(srcDir), target = outDir, classpath = scripDefinitionCompilationClassPath + listOf(outDir),
        compileKotlinSourcesBeforeJava = false
    ).compile()
    return outDir
}

private fun registerScriptDefinitionsProvider(project: Project, testRootDisposable: Disposable, environment: Environment) {
    val provider = CustomScriptTemplateProvider(environment)

    addExtensionPointInTest(SCRIPT_DEFINITIONS_SOURCES, project, provider, testRootDisposable)

    ScriptDefinitionsManager.getInstance(project).reloadDefinitions()

    // a.kts and b.kts definitions should go before default .kts one
    val scriptingSettings = KotlinScriptingSettingsImpl.getInstance(project)
    provider.definitions.forEach {
        scriptingSettings.setOrder(it, 1)
    }
    ScriptDefinitionsManager.getInstance(project).reorderDefinitions()
}

class TestEnvironment(val env: MutableMap<String, Any>) {
    fun update(aKtsLibs: List<File>? = null, bKtsLibs: List<File>? = null) {
        aKtsLibs?.let { env["lib-classes-A"] = it }
        bKtsLibs?.let { env["lib-classes-B"] = it }
    }
}
