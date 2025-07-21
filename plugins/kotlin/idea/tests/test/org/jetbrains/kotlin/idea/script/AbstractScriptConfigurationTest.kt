// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.jdom.Element
import org.jetbrains.kotlin.idea.base.highlighting.shouldHighlightFile
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.completion.test.KotlinCompletionTestCase
import org.jetbrains.kotlin.idea.core.script.shared.getScriptReports
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager.Companion.updateScriptDependenciesSynchronously
import org.jetbrains.kotlin.idea.core.script.k1.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.script.AbstractScriptConfigurationTest.Companion.useDefaultTemplate
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.projectStructure.getModuleDir
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.test.util.projectLibrary
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.script.dependencies.Environment
import kotlin.script.experimental.api.ScriptDiagnostic

// some bugs can only be reproduced when some module and script have intersecting library dependencies
private const val configureConflictingModule = "// CONFLICTING_MODULE"

private fun String.splitOrEmpty(delimeters: String) = split(delimeters).takeIf { it.size > 1 } ?: emptyList()
internal val switches = listOf(
    useDefaultTemplate,
    configureConflictingModule
)

abstract class AbstractScriptConfigurationTest : KotlinCompletionTestCase() {
    companion object {
        private const val SCRIPT_NAME = "script.kts"

        val validKeys = setOf("javaHome", "sources", "classpath", "imports", "template-classes-names")
        const val useDefaultTemplate = "// DEPENDENCIES:"
        const val templatesSettings = "// TEMPLATES: "
    }

    override fun setUpModule() {
        // do not create default module
    }

    protected fun findMainScript(testDir: File): File {
        testDir.walkTopDown().find { it.name == SCRIPT_NAME }?.let { return it }

        return testDir.walkTopDown().singleOrNull { it.name.contains("script") }
            ?: error("Couldn't find $SCRIPT_NAME file in $testDir")
    }

    private val sdk by lazy {
        runWriteAction {
            val sdk = PluginTestCaseBase.addJdk(testRootDisposable) { IdeaTestUtil.getMockJdk18() }
            ProjectRootManager.getInstance(project).projectSdk = sdk
            sdk
        }
    }

    protected fun configureScriptFile(path: File): VirtualFile {
        val mainScriptFile = findMainScript(path)
        return configureScriptFile(path, mainScriptFile)
    }

    protected fun configureScriptFile(path: File, mainScriptFile: File): VirtualFile {
        val environment = createScriptEnvironment(mainScriptFile)
        registerScriptTemplateProvider(environment)

        File(path, "mainModule").takeIf { it.exists() }?.let {
            myModule = createTestModuleFromDir(it)
        }

        path.listFiles { file -> file.name.startsWith("module") }
            ?.filter { it.exists() }
            ?.forEach {
                val newModule = createTestModuleFromDir(it)
                assert(myModule != null) { "Main module should exists" }
                ModuleRootModificationUtil.addDependency(myModule, newModule)
            }

        path.listFiles { file ->
            file.name.startsWith("script") && file.name != SCRIPT_NAME
        }?.forEach {
            createFileAndSyncDependencies(it)
        }

        // If script is inside module
        if (module != null && mainScriptFile.parentFile.name.lowercase(Locale.getDefault()).contains("module")) {
            module.addDependency(
                projectLibrary(
                    "script-runtime",
                    classesRoot = VfsUtil.findFileByIoFile(TestKotlinArtifacts.kotlinScriptRuntime, true)
                )
            )

            if (environment["template-classes"] != null) {
                environment.toVirtualFiles("template-classes").forEach {
                    module.addDependency(
                        projectLibrary("script-template-library", classesRoot = it)
                    )
                }
            }
        }

        if (configureConflictingModule in environment) {
            if (module == null) {
                // Force create module if it doesn't exist
                myModule = createTestModuleByName("mainModule")
            }
            val sharedLib = environment.toVirtualFiles("lib-classes")
            val sharedLibSources = environment.toVirtualFiles("lib-source")
            check(sharedLib.size == sharedLibSources.size) { "$sharedLib and $sharedLibSources must have the same length" }
            sharedLib.zip(sharedLibSources).forEachIndexed { index, (lib, sources) ->
                module.addDependency(projectLibrary("sharedLib_$index", classesRoot = lib, sourcesRoot = sources))
            }
        }

        module?.let {
            ModuleRootModificationUtil.updateModel(it) { model ->
                model.sdk = sdk
            }
        }

        return createFileAndSyncDependencies(mainScriptFile)
    }

    private fun Environment.toVirtualFiles(name: String): List<VirtualFile> {
        val list = this[name] as? List<Any?> ?: error("'$name' must be a List")
        return list.map { it as? File ?: error("'$name' list must contain only Files") }
            .map { VfsUtil.findFileByIoFile(it, true) ?: error("unable to look up a virtual file for $name: $it") }
    }

    private val oldScripClasspath: String? = System.getProperty("kotlin.script.classpath")

    private var settings: Element? = null

    override fun setUp() {
        super.setUp()

        settings = KotlinScriptingSettingsImpl.getInstance(project).state

        ScriptDefinitionsManager.getInstance(project).getDefinitions().forEach {
            KotlinScriptingSettingsImpl.getInstance(project).setEnabled(it, false)
        }

        setUpTestProject()
    }

    open fun setUpTestProject() {

    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { System.setProperty("kotlin.script.classpath", oldScripClasspath ?: "") },
            ThrowableRunnable {
                settings?.let {
                    KotlinScriptingSettingsImpl.getInstance(project).loadState(it)
                }
            },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun getTestProjectJdk(): Sdk {
        return IdeaTestUtil.getMockJdk18()
    }

    protected fun createTestModuleByName(name: String): Module {
        val path = File(project.basePath, name).path
        val newModuleDir = runWriteAction { VfsUtil.createDirectoryIfMissing(path) ?: error("unable to create $path") }
        val newModule = createModuleAt(name, project, JavaModuleType.getModuleType(), Paths.get(newModuleDir.path))

        PsiTestUtil.addSourceContentToRoots(newModule, newModuleDir)
        return newModule
    }

    private fun createTestModuleFromDir(dir: File): Module {
        return createTestModuleByName(dir.name).apply {
            val findFileByIoFile = LocalFileSystem.getInstance().findFileByIoFile(dir) ?: error("unable to locate $dir")
            HeavyPlatformTestCase.copyDirContentsTo(findFileByIoFile, contentRoot())
        }
    }

    private fun Module.contentRoot() = ModuleRootManager.getInstance(this).contentRoots.first()

    private fun createScriptEnvironment(scriptFile: File): Environment {
        val defaultEnvironment = defaultEnvironment(scriptFile.parent)
        val env = mutableMapOf<String, Any?>()
        scriptFile.forEachLine { line ->

            fun iterateKeysInLine(prefix: String) {
                if (line.contains(prefix)) {
                    line.trim().substringAfter(prefix).split(";").forEach { entry ->
                        val (key, values) = entry.splitOrEmpty(":").map { it.trim() }
                        assert(key in validKeys) { "Unexpected key: $key" }
                        env[key] = values.split(",").flatMap {
                            val str = it.trim()
                            defaultEnvironment[str] ?: listOf(str)
                        }
                    }
                }
            }

            iterateKeysInLine(useDefaultTemplate)
            iterateKeysInLine(templatesSettings)

            switches.forEach {
                if (it in line) {
                    env[it] = true
                }
            }
        }

        if (env[useDefaultTemplate] != true && env["template-classes-names"] == null) {
            env["template-classes-names"] = listOf("custom.scriptDefinition.Template")
        }

        val jdkKind = when ((env["javaHome"] as? List<String>)?.singleOrNull()) {
            "9" -> TestJdkKind.FULL_JDK_11 // TODO is that correct?
            else -> TestJdkKind.MOCK_JDK
        }
        runWriteAction {
            val jdk = PluginTestCaseBase.addJdk(testRootDisposable) {
                PluginTestCaseBase.jdk(jdkKind)
            }
            env["javaHome"] = File(jdk.homePath!!)
        }

        env.putAll(defaultEnvironment)
        return env
    }

    private fun defaultEnvironment(path: String): Map<String, List<File>> {
        val templateOutDir = File(path, "template").takeIf { it.isDirectory }?.let {
            compileLibToDir(it, getScriptingClasspath())
        } ?: testDataFile("../defaultTemplate").takeIf { it.isDirectory }?.let {
            compileLibToDir(it, getScriptingClasspath())
        }

        if (templateOutDir != null) {
            System.setProperty("kotlin.script.classpath", templateOutDir.path)
        } else {
            LOG.warn("templateOutDir is not found")
        }

        val libSrcDir = File(path, "lib").takeIf { it.isDirectory }

        val libClasses = libSrcDir?.let { compileLibToDir(it, emptyList()) }

        var moduleSrcDir = File(path, "depModule").takeIf { it.isDirectory }
        val moduleClasses = moduleSrcDir?.let { compileLibToDir(it, emptyList()) }
        if (moduleSrcDir != null) {
            val depModule = createTestModuleFromDir(moduleSrcDir)
            moduleSrcDir = File(depModule.getModuleDir())
        }

        return buildMap {
            put("runtime-classes", listOf(TestKotlinArtifacts.kotlinStdlib))
            put("runtime-source", listOf(TestKotlinArtifacts.kotlinStdlibSources, TestKotlinArtifacts.kotlinStdlibCommonSources))
            libClasses?.let { put("lib-classes", listOf(it)) }
            libSrcDir?.let { put("lib-source", listOf(it)) }
            moduleClasses?.let { put("module-classes", listOf(it)) }
            moduleSrcDir?.let { put("module-source", listOf(it)) }
            templateOutDir?.let { put("template-classes", listOf(it)) }
        }
    }

    protected fun getScriptingClasspath(): List<File> {
        return listOf(
          TestKotlinArtifacts.kotlinScriptRuntime,
          TestKotlinArtifacts.kotlinScriptingCommon,
          TestKotlinArtifacts.kotlinScriptingJvm,
        )
    }

    protected fun createFileAndSyncDependencies(scriptFile: File): VirtualFile {
        var script: VirtualFile? = null
        if (module != null) {
            val contentRoot = module.contentRoot()
            script = contentRoot.findChild(scriptFile.name)
        }

        if (script == null) {
            val target = File(project.basePath, scriptFile.name)
            scriptFile.copyTo(target)
            script = VfsUtil.findFileByIoFile(target, true)
        }

        script ?: error("Test file with script couldn't be found in test project")

        configureByExistingFile(script)
        loadScriptConfigurationSynchronously(script)
        return script
    }

    protected open fun loadScriptConfigurationSynchronously(script: VirtualFile) {
        updateScriptDependenciesSynchronously(myFile)

        // This is needed because updateScriptDependencies invalidates psiFile that was stored in myFile field
        VfsUtil.markDirtyAndRefresh(false, true, true, project.baseDir)
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        myFile = psiManager.findFile(script)

        checkHighlighting()
    }

    protected fun checkHighlighting(file: KtFile = myFile as KtFile) {
        val reports = getScriptReports(file)
        val isFatalErrorPresent = reports.any { it.severity == ScriptDiagnostic.Severity.FATAL }
        assert(isFatalErrorPresent || file.shouldHighlightFile()) {
            "Highlighting is switched off for ${file.virtualFile.path}\n" +
                    "reports=$reports\n" +
                    "scriptDefinition=${file.findScriptDefinition()}"
        }
    }

    private fun compileLibToDir(srcDir: File, classpath: List<File>): File {
        val outDir = KotlinTestUtils.tmpDirForReusableFolder("${getTestName(false)}${srcDir.name}Out")
        KotlinCompilerStandalone(
            listOf(srcDir),
            target = outDir,
            classpath = classpath + listOf(outDir),
            options = listOf("-Xabi-stability=stable"),
            compileKotlinSourcesBeforeJava = false
        ).compile()
        return outDir
    }

    private fun registerScriptTemplateProvider(environment: Environment) {
        val provider = if (environment[useDefaultTemplate] == true) {
            FromTextTemplateProvider(environment)
        } else {
            CustomScriptTemplateProvider(environment)
        }

        addExtensionPointInTest(
            SCRIPT_DEFINITIONS_SOURCES,
            project,
            provider,
            testRootDisposable
        )

        ScriptDefinitionsManager.getInstance(project).reloadDefinitions()

        UIUtil.dispatchAllInvocationEvents()
    }
}
