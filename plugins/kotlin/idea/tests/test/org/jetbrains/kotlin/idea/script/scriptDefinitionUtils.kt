// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.k1.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File
import java.nio.file.Path
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
    aKtsLibs: List<Path>,
    bKtsLibs: List<Path>
): TestEnvironment {

    compileLibToDir(testName, File(scriptDefinitionSourcePath))
    return TestEnvironment(
        mutableMapOf(
            "lib-classes-A" to aKtsLibs.map { it.toFile() },
            "lib-classes-B" to bKtsLibs.map { it.toFile() },
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
        listOf(srcDir), target = outDir, classpath = scripDefinitionCompilationClassPath.map { it.toFile() } + listOf(outDir),
        compileKotlinSourcesBeforeJava = false
    ).compile()
    return outDir
}

private fun registerScriptDefinitionsProvider(project: Project, testRootDisposable: Disposable, environment: Environment) {
    val provider = CustomScriptTemplateProvider(environment)

    addExtensionPointInTest(SCRIPT_DEFINITIONS_SOURCES, project, provider, testRootDisposable)

    ScriptDefinitionsManager.getInstance(project).reloadDefinitions()

    // a.kts and b.kts definitions should go before default .kts one
    val scriptingSettings = KotlinScriptingSettings.getInstance(project)
    provider.definitions.forEach {
        scriptingSettings.setOrder(it, 1)
    }
    ScriptDefinitionsManager.getInstance(project).reorderDefinitions()
}

class TestEnvironment(val env: MutableMap<String, Any>) {
    fun update(aKtsLibs: List<Path>? = null, bKtsLibs: List<Path>? = null) {
        aKtsLibs?.let { libs -> env["lib-classes-A"] = libs.map { lib -> lib.toFile() } }
        bKtsLibs?.let { libs -> env["lib-classes-B"] = libs.map { lib -> lib.toFile() } }
    }
}
