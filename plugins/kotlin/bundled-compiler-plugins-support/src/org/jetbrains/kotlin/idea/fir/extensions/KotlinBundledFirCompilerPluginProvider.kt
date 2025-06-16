// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * An extension point which allows substituting
 * Kotlin FIR compiler plugins at runtime with bundled versions.
 *
 * The purpose of that is to replace the compiler plugin jar
 * supplied by user with a jar which is binary compatible
 * with the Kotlin compiler frontend bundled
 * into the Kotlin IntelliJ Plugin.
 *
 * Note: This EP is only used in K2 Mode of the Kotlin IntelliJ Plugin.
 * It is ignored in K1 Mode.
 *
 * WARNING: This is a highly experimental API with no stability guarantees.
 * It will most definitely be a subject of change in the future.
 *
 * @see CompilerPluginRegistrarUtils
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface KotlinBundledFirCompilerPluginProvider {
    /**
     * For the recognised [userSuppliedPluginJar]s,
     * returns [Path] pointing to the bundled variant of the same compiler plugin jar,
     * and `null` otherwise.
     *
     * This method can be executed quite frequently, so it should be reasonably fast
     * and avoid any heavy IO blocking (network access, for example).
     */
    fun provideBundledPluginJar(project: Project, userSuppliedPluginJar: Path): Path? =
        provideBundledPluginJar(userSuppliedPluginJar)

    @Deprecated("Will be removed; override 'provideBundledPluginJar(Project, Path)' instead.")
    fun provideBundledPluginJar(userSuppliedPluginJar: Path): Path? {
        throw AbstractMethodError("'provideBundledPluginJar(Project, Path)' must be implemented in ${this::class}")
    }

    companion object {
        private val EP_NAME: ExtensionPointName<KotlinBundledFirCompilerPluginProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.bundledFirCompilerPluginProvider")

        fun provideBundledPluginJar(project: Project, originalPluginJar: Path): Path? {
            return EP_NAME.extensionList.firstNotNullOfOrNull {
                ProgressManager.checkCanceled()
                it.provideBundledPluginJar(project, originalPluginJar)
            }
        }
    }
}