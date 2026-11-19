// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.openapi.util.Key
import com.intellij.util.lang.Xxh3
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.jvm.JvmDependency

private val TEMPLATE_FINGERPRINT: Key<Long> = Key.create("TEMPLATE_FINGERPRINT")

/**
 * The paths of the template classpath, and the path, the size and the modification time of the template files:
 * the jar of the template class, or the files of its package in a class directory.
 * A build that rewrites the template changes it, also when the template keeps its name. A changed classpath changes it too.
 * A definition without a classpath and without a loaded template class has 0.
 */
val ScriptDefinition.templateFingerprint: Long
    get() = getUserData(TEMPLATE_FINGERPRINT) ?: computeTemplateFingerprint().also { putUserData(TEMPLATE_FINGERPRINT, it) }

@Suppress("IO_FILE_USAGE")
private fun ScriptDefinition.computeTemplateFingerprint(): Long {
    val classpath = hostConfiguration[ScriptingHostConfiguration.configurationDependencies].orEmpty()
        .flatMap { (it as? JvmDependency)?.classpath.orEmpty() }
        .map { it.path }
    val templateFiles = templateFileStamps()
    if (classpath.isEmpty() && templateFiles.isEmpty()) return 0
    return Xxh3.hash((classpath + templateFiles).joinToString("\n").toByteArray())
}

private fun ScriptDefinition.templateFileStamps(): List<String> {
    val templateClass = compilationConfiguration[ScriptCompilationConfiguration.baseClass]?.fromClass?.java ?: return emptyList()
    val location = templateClass.protectionDomain?.codeSource?.location ?: return emptyList()
    return try {
        val root = Path.of(location.toURI())
        val files = if (root.isDirectory()) {
            root.resolve(templateClass.packageName.replace('.', '/')).listDirectoryEntries().filter { it.isRegularFile() }.sorted()
        }
        else {
            listOf(root)
        }
        files.map { "$it ${it.fileSize()} ${it.getLastModifiedTime().toMillis()}" }
    }
    catch (_: IOException) {
        emptyList()
    }
    catch (_: IllegalArgumentException) {
        emptyList()
    }
}
