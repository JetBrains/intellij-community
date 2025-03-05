// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.diagnostic.PluginException
import com.intellij.execution.wsl.WslPath.Companion.isWslUncPath
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.ExceptionUtil
import com.intellij.util.PathUtil
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition.FromLegacy
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.jvm.JvmDependency


fun loadDefinitionsFromTemplates(
    templateClassNames: List<String>,
    templateClasspath: List<File>,
    baseHostConfiguration: ScriptingHostConfiguration,
    // TODO: need to provide a way to specify this in compiler/repl .. etc
    /*
     * Allows to specify additional jars needed for DependenciesResolver (and not script template).
     * Script template dependencies naturally become (part of) dependencies of the script which is not always desired for resolver dependencies.
     * i.e. gradle resolver may depend on some jars that 'built.gradle.kts' files should not depend on.
     */
    additionalResolverClasspath: List<File> = emptyList(),
    defaultCompilerOptions: Iterable<String> = emptyList()
): List<ScriptDefinition> = loadDefinitionsFromTemplatesByPaths(
    templateClassNames,
    templateClasspath.map(File::toPath),
    baseHostConfiguration,
    additionalResolverClasspath.map(File::toPath),
    defaultCompilerOptions
)

// TODO: consider rewriting to return sequence
fun loadDefinitionsFromTemplatesByPaths(
    templateClassNames: List<String>,
    templateClasspath: List<Path>,
    baseHostConfiguration: ScriptingHostConfiguration,
    // TODO: need to provide a way to specify this in compiler/repl .. etc
    /*
     * Allows to specify additional jars needed for DependenciesResolver (and not script template).
     * Script template dependencies naturally become (part of) dependencies of the script which is not always desired for resolver dependencies.
     * i.e. gradle resolver may depend on some jars that 'built.gradle.kts' files should not depend on.
     */
    additionalResolverClasspath: List<Path> = emptyList(),
    defaultCompilerOptions: Iterable<String> = emptyList()
): List<ScriptDefinition> {
    val classpath = adjustClasspath(templateClasspath + additionalResolverClasspath)
    scriptingInfoLog("Loading script definitions: classes = $templateClassNames, classpath = ${classpath}")

    val baseLoader = ScriptDefinitionsSource::class.java.classLoader
    val loader = if (classpath.isEmpty())
        baseLoader
    else
        UrlClassLoader.build().files(classpath).parent(baseLoader).get()

    val definitions = templateClassNames.mapNotNull { templateClassName ->
        try {
            // TODO: drop class loading here - it should be handled downstream
            // as a compatibility measure, the asm based reading of annotations should be implemented to filter classes before classloading
            val template = loader.loadClass(templateClassName).kotlin
            // do not use `Path::toFile` here as it might break the path format of non-local file system
            val templateClasspathAsFiles = templateClasspath.map { File(it.toString()) }
            val hostConfiguration = ScriptingHostConfiguration(baseHostConfiguration) {
                configurationDependencies(JvmDependency(templateClasspathAsFiles))
            }

            when {
                template.annotations.firstIsInstanceOrNull<kotlin.script.experimental.annotations.KotlinScript>() != null -> {
                    ScriptDefinition.FromTemplate(hostConfiguration, template, ScriptDefinition::class, defaultCompilerOptions)
                }

                template.annotations.firstIsInstanceOrNull<kotlin.script.templates.ScriptTemplateDefinition>() != null -> {
                    FromLegacy(
                        hostConfiguration,
                        KotlinScriptDefinitionFromAnnotatedTemplate(
                            template,
                            hostConfiguration[ScriptingHostConfiguration.getEnvironment]?.invoke(),
                            templateClasspathAsFiles
                        ),
                        defaultCompilerOptions
                    )
                }

                else -> {
                    scriptingWarnLog("Cannot find a valid script definition annotation on the class $template")
                    null
                }
            }
        } catch (e: ClassNotFoundException) {
            // Assuming that direct ClassNotFoundException is the result of versions mismatch and missing subsystems, e.g. gradle
            // so, it only results in warning, while other errors are severe misconfigurations, resulting it user-visible error
            scriptingWarnLog("Cannot load script definition class $templateClassName", e)
            null
        } catch (e: Throwable) {
            if (e is ControlFlowException) {
                throw e
            }

            val message = "Cannot load script definition class $templateClassName"
            PluginManager.getPluginByClassNameAsNoAccessToClass(templateClassName)?.let {
                scriptingErrorLog(message, PluginException(message, e, it))
            } ?: scriptingErrorLog(message, e)
            null
        }
    }

    scriptingInfoLog("Loaded definitions: classes = $templateClassNames, definitions = ${definitions.map { it.name }}")
    return definitions
}

private fun adjustClasspath(source: List<Path>): List<Path> {
    val cacheFolder = lazy {
        val tempDirectory = FileUtil.getTempDirectory()
        tempDirectory.toNioPathOrNull()
            ?.resolve("kotlin-script-dependencies")
            ?.findOrCreateDirectory()
    }
    return source.map {
        if (it.shouldBeMovedToHost()) {
            return@map moveJarFromWslToHost(it, cacheFolder)
        }
        return@map it
    }
}

/**
 * This workaround prevents Gradle sync from hanging up.
 * Slow file read caused by a bug in p9rdr.sys (the plan 9 redirector driver), which handles the \wsl$<distro> file accesses.
 * The bug causes a transaction to be left in a pending state forever resulting in the freeze for an enormous amount of time.
 * This workaround is only applied on Windows with a project open from a WSL machine.
 * The copy operation has no side effects from the bug in p9, and we can avoid the freeze.
 */
private fun moveJarFromWslToHost(source: Path, targetFolderPathResolver: Lazy<Path?>): Path {
    val targetFolderPath = targetFolderPathResolver.value ?: return source
    val fileNameWithExtension = PathUtil.getFileName(source.toCanonicalPath())
    val relocatedJar = targetFolderPath.resolve(fileNameWithExtension)
    if (relocatedJar.exists()) {
        return relocatedJar
    }
    try {
        Files.copy(source, relocatedJar, StandardCopyOption.REPLACE_EXISTING)
    } catch (t: Throwable) {
        logger.warn("Unable to copy a DSL-related jar from $source to $relocatedJar: ${ExceptionUtil.getMessage(t)}")
        return source
    }
    return relocatedJar
}

private fun Path.shouldBeMovedToHost(): Boolean {
    return isWslUncPath(pathString) && PathUtil.getFileName(toCanonicalPath()).endsWith(".jar")
}