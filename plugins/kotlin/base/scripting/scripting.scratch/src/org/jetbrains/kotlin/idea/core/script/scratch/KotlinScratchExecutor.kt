// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.JavaParametersBuilder
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.PathUtil
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.ide.kotlinc.AnalyzerKotlincProvider
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.script.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KOTLIN_SCRATCH_EXPLAIN_FILE
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KotlinScratchExplainScript
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KotlinScratchPlainScript
import org.jetbrains.kotlin.idea.core.script.scratch.output.ExplainInfo
import org.jetbrains.kotlin.idea.core.script.scratch.output.ScratchOutput
import org.jetbrains.kotlin.idea.core.script.scratch.output.ScratchOutputHandler
import org.jetbrains.kotlin.idea.core.script.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.core.script.scratch.output.ScratchOutputType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readLines

private val log = Logger.getInstance(KotlinScratchExecutor::class.java)

class KotlinScratchExecutor(val scratchFile: KotlinScratchFile, val project: Project, val scope: CoroutineScope) {

    private val handler: CompositeOutputHandler = CompositeOutputHandler()

    init {
        addOutputHandler(object : ScratchOutputHandlerAdapter() {
            override fun onStart(file: KotlinScratchFile) {
                ScratchCompilationSupport.start(file, this@KotlinScratchExecutor)
                // execution state changes without user activity, so toolbar actions need a nudge
                ActivityTracker.getInstance().inc()
            }

            override fun onFinish(file: KotlinScratchFile) {
                ScratchCompilationSupport.stop()
                ActivityTracker.getInstance().inc()
            }
        })
    }

    fun execute() {
        if (scratchFile.jdk == null) {
            handler.error(scratchFile, KotlinScratchBundle.message("scratch.no.jdk.selected"))
            return
        }

        handler.onStart(scratchFile)

        scope.launch {
            try {
                processExecution(scratchFile.virtualFile)
            } catch (e: Exception) {
                handler.error(scratchFile, e.message ?: "Unknown error")
            } finally {
                handler.onFinish(scratchFile)
            }
        }
    }

    private suspend fun processExecution(scriptFile: VirtualFile) {
        val document = readAction { scriptFile.findDocument() }
        if (document == null) {
            handler.error(scratchFile, "Cannot find document: ${scriptFile.path}")
            return
        }

        edtWriteAction {
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        }
        val isExplainEnabled = scratchFile.options.isExplainEnabled

        val (code, stdout, stderr) = withBackgroundProgress(
            project, title = KotlinScratchBundle.message("progress.title.compiling.kotlin.scratch")
        ) {
            val scratchCompilerHome = reportSequentialProgress { reporter ->
                reporter.itemStep(KotlinBasePluginBundle.message("progress.text.kotlin.scratch.compiler.prepare")) {
                    try {
                        AnalyzerKotlincProvider.getKotlincHome()
                    } catch (e: Exception) {
                        rethrowControlFlowException(e)
                        log.warn("Falling back to the bundled JPS compiler at ${KotlinPluginLayout.kotlincPath}", e)
                        KotlinPluginLayout.kotlincPath
                    }
                }
            }

            val scratchCompilerJar = scratchCompilerArtifact(scratchCompilerHome, KotlinArtifactNames.KOTLIN_COMPILER)
            log.info("Using scratch compiler: home=$scratchCompilerHome, compilerJar=$scratchCompilerJar")

            val process = getJavaCommandLine(
                scratchFile.virtualFile,
                scratchFile.module,
                scratchCompilerHome,
                isExplainEnabled,
            ).createProcess()
            process.awaitExit()
            val stdout = withContext(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderr = withContext(Dispatchers.IO) {
                process.errorStream.bufferedReader().use { it.readText() }
            }

            CompilationResult(process.exitValue(), stdout, stderr)
        }

        if (code == 0) {
            if (stdout.isNotEmpty()) {
                handler.handle(scratchFile, ScratchOutput(stdout, ScratchOutputType.OUTPUT))
            }

            if (isExplainEnabled) {
                handler.handle(scratchFile, readExplanations(scriptFile), scope)
            }
        } else if (!scratchFile.options.isInteractiveMode) {
            handler.error(scratchFile, "Compilation failed: $stderr")
        }
    }

    private fun readExplanations(scriptFile: VirtualFile): List<ExplainInfo> {
        if (!Files.exists(scriptFile.explainFilePath)) return emptyList()

        return scriptFile.explainFilePath.readLines().associate {
            it.substringBefore('=', "") to unescapeExplainValue(it.substringAfter('='))
        }.filterKeys { it.isNotBlank() }.map { (key, value) ->
            val leftBracketIndex = key.indexOf("(")
            val rightBracketIndex = key.indexOf(")")
            val commaIndex = key.indexOf(",")

            val offsets =
                key.substring(leftBracketIndex + 1, commaIndex).trim().toInt() to key.substring(commaIndex + 1, rightBracketIndex)
                    .trim().toInt()

            ExplainInfo(
                key.substring(0, leftBracketIndex), offsets, value, scratchFile.getPsiFile()?.getLineNumber(offsets.first)
            )
        }
    }

    private fun getJavaCommandLine(
        scriptVirtualFile: VirtualFile,
        module: Module?,
        scratchCompilerHome: Path,
        isExplainEnabled: Boolean,
    ): GeneralCommandLine {
        val javaParameters =
            JavaParametersBuilder(project)
                .withMainClassName("org.jetbrains.kotlin.preloading.Preloader")
                .build()
        javaParameters.jdk = checkNotNull(scratchFile.jdk) {
            "Scratch JDK must be selected before execution; guarded by execute() and RunKotlinScratchAction.update()."
        }
        javaParameters.useDynamicClasspathDefinedByJdkLevel()
        // The compiler classpath is a program parameter, and only the arg file can carry those: the other methods route
        // them through CommandLineWrapper, which refuses to run on Java 9+
        javaParameters.setUseDynamicParameters(javaParameters.isArgFile)
        javaParameters.setUseDynamicVMOptions(javaParameters.isArgFile)

        javaParameters.charset = null
        if (isExplainEnabled) {
            javaParameters.vmParametersList.add("-D$KOTLIN_SCRATCH_EXPLAIN_FILE=${scriptVirtualFile.explainFilePath}")
        }

        val classPath = buildSet {
            add(ideaScriptingJar)
            addAll(requiredKotlinArtifacts(scratchCompilerHome, isExplainEnabled))
            if (module != null) addAll(JavaParametersBuilder.getModuleDependencies(module))
        }

        javaParameters.classPath.add(
            scratchCompilerArtifact(
                scratchCompilerHome, KotlinArtifactNames.KOTLIN_PRELOADER
            ).absolutePathString()
        )
        val scriptTemplate = if (isExplainEnabled) {
            KotlinScratchExplainScript::class.java
        } else {
            KotlinScratchPlainScript::class.java
        }

        @Suppress("IO_FILE_USAGE")
        val programParameters = buildList {
            addAll(
                listOf(
                    "-cp",
                    scratchCompilerArtifact(scratchCompilerHome, KotlinArtifactNames.KOTLIN_COMPILER).absolutePathString(),
                    "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-cp",
                    classPath.joinToString(java.io.File.pathSeparator),
                    "-kotlin-home",
                    scratchCompilerHome.absolutePathString(),
                    "-script",
                    scriptVirtualFile.path,
                )
            )

            if (isExplainEnabled) {
                val powerAssertPluginPath = scratchCompilerArtifact(
                    scratchCompilerHome, KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN
                ).absolutePathString()
                add("-Xplugin=$powerAssertPluginPath")
            }

            addAll(
                listOf(
                    "-P",
                    "plugin:kotlin.scripting:script-templates=${scriptTemplate.name}",
                    "-Xallow-any-scripts-in-source-roots",
                    "-P",
                    "plugin:kotlin.scripting:disable-script-definitions-autoloading=true",
                    "-P",
                    "plugin:kotlin.scripting:disable-standard-script=true",
                )
            )

            if (isExplainEnabled) {
                add("-P")
                add("plugin:kotlin.scripting:enable-script-explanation=true")
            }
        }

        javaParameters.programParametersList.addAll(programParameters)

        val commandLine = javaParameters.toCommandLine()
        log.info("commandLine=${commandLine.commandLineString}")

        return commandLine
    }

    private fun requiredKotlinArtifacts(scratchCompilerHome: Path, includePowerAssert: Boolean): List<String> =
        kotlincIdeScratchClasspathArtifactFileNames
            .filter { includePowerAssert || it != KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN }
            .map { scratchCompilerArtifact(scratchCompilerHome, it) }
            .filter(Files::exists).map(Path::absolutePathString)

    private fun scratchCompilerArtifact(scratchCompilerHome: Path, fileName: String): Path =
        scratchCompilerHome.resolve("lib").resolve(fileName)

    private val ideaScriptingJar by lazy { PathUtil.getJarPathForClass(KotlinScratchExplainScript::class.java) }

    private val explainScratchesDirectory: Path by lazy {
        FileUtilRt.createTempDirectory("kotlin-scratches-explain", null, true).toPath()
    }

    private val VirtualFile.explainFilePath: Path
        get() = explainScratchesDirectory.resolve(this.name.replace(".kts", ".txt"))

    fun stop() {
        handler.onFinish(scratchFile)
    }

    fun addOutputHandler(outputHandler: ScratchOutputHandler) {
        handler.add(outputHandler)
    }

    fun errorOccurs(message: String, e: Throwable? = null, isFatal: Boolean = false) {
        handler.error(scratchFile, message)

        if (isFatal) {
            handler.onFinish(scratchFile)
        }

        if (e != null && e !is ControlFlowException) LOG.error(e)
    }

    private fun unescapeExplainValue(value: String): String =
        value.replace("\\\\", "\u0000").replace("\\n", "\n").replace("\\r", "\r").replace("\u0000", "\\")

    private data class CompilationResult(
        val code: Int,
        val stdout: String,
        val stderr: String,
    )

    private class CompositeOutputHandler : ScratchOutputHandler {
        private val handlers = mutableSetOf<ScratchOutputHandler>()

        fun add(handler: ScratchOutputHandler) {
            handlers.add(handler)
        }

        override fun onStart(file: KotlinScratchFile) {
            handlers.forEach { it.onStart(file) }
        }

        override fun handle(file: KotlinScratchFile, explanations: List<ExplainInfo>, scope: CoroutineScope) {
            handlers.forEach { it.handle(file, explanations, scope) }
        }

        override fun handle(file: KotlinScratchFile, output: ScratchOutput) {
            handlers.forEach { it.handle(file, output) }
        }

        override fun error(file: KotlinScratchFile, message: String) {
            handlers.forEach { it.error(file, message) }
        }

        override fun onFinish(file: KotlinScratchFile) {
            handlers.forEach { it.onFinish(file) }
        }

        override fun clear(file: KotlinScratchFile) {
            handlers.forEach { it.clear(file) }
        }
    }
}
