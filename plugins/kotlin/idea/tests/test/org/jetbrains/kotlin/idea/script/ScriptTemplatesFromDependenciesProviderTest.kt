// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.roots.invalidateProjectRoots
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_PATH
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.test.*
import org.jetbrains.kotlin.test.util.addDependency
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.projectLibrary
import org.junit.runner.RunWith
import org.jetbrains.kotlin.idea.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

@TestRoot("idea/tests")
@TestDataPath("\$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners::class)
@TestMetadata("testData/script/definition/jar")
class ScriptTemplatesFromDependenciesProviderTest : AbstractScriptConfigurationTest() {
    override fun setUp() {
        super.setUp()

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        runWriteAction {
            project.invalidateProjectRoots()
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    @TestMetadata("customDefinitionInJar")
    fun testCustomDefinitionInJar() {
        val allDefinitions = run {
            var definitions = emptyList<ScriptDefinition>()
            val timeoutMs = 100L
            val attempts = 20
            // definitions are loaded in a background, spin some time to get them loaded
            for (attempt in 0 until attempts) {
                definitions = ScriptDefinitionsManager.getInstance(project).getAllDefinitions()
                if (definitions.any { it.name == "Custom Init Script" }) {
                    return@run definitions
                }
                Thread.sleep(timeoutMs)
            }

            definitions
        }
        assertTrue(allDefinitions.joinToString { it.name }, allDefinitions.any { it.name == "Custom Init Script" })
        assertTrue(allDefinitions.joinToString { it.name }, allDefinitions.any { it.name == "Custom Setting Script" })
    }

    private var customScriptDefinitionsJar: File? = null

    override fun setUpTestProject() {
        myModule = createMainModule()
        customScriptDefinitionsJar = buildJar()
        module.addDependency(projectLibrary("customScriptDefinitionsLib", classesRoot = customScriptDefinitionsJar?.jarRoot))
    }

    override fun tearDown(): Unit = runAll(
        ThrowableRunnable {
            customScriptDefinitionsJar?.delete()
            customScriptDefinitionsJar = null
        },
        ThrowableRunnable { super.tearDown() },
    )

    private fun buildJar(): File {
        val lib = File(testDataFile(), "lib").takeIf { it.isDirectory }!!
        val templateOutDir = compileLibToDir(lib, getScriptingClasspath())
        buildScriptDefinitionMarkers(templateOutDir, lib)

        return KotlinCompilerStandalone.copyToJar(listOf(templateOutDir), "custom-script-definitions")
    }

    private fun buildScriptDefinitionMarkers(templateOutDir: File, lib: File) {
        val scriptDefinitionMarkersPath = File(templateOutDir, SCRIPT_DEFINITION_MARKERS_PATH)
        assertTrue(scriptDefinitionMarkersPath.mkdirs())
        lib.walk().forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val relativePath = FileUtil.getRelativePath(lib, dir)?.replace("/", ".")!!
            dir.listFiles()?.filter { it.isFile }?.forEach {
                val fileName = it.name.removeSuffix("." + it.extension)
                val fqName = fileName + (if (fileName.endsWith("WithSuffix")) SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT else "")
                val file = File(scriptDefinitionMarkersPath, "$relativePath.$fqName")
                assertTrue(file.createNewFile())
            }
        }
    }

    private fun compileLibToDir(srcDir: File, classpath: List<File>): File {
        val outDir = KotlinTestUtils.tmpDirForReusableFolder("${getTestName(false)}${srcDir.name}Out")
        KotlinCompilerStandalone(
            listOf(srcDir),
            target = outDir,
            classpath = classpath + listOf(outDir),
            compileKotlinSourcesBeforeJava = false
        ).compile()
        return outDir
    }
}