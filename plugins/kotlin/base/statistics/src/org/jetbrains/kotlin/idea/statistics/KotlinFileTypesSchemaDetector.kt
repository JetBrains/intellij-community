// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class KotlinFileTypeSchemaDetector : FileTypeUsageSchemaDescriptor {
    override fun describes(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".kt", true)
}

internal enum class KotlinScriptTypes(val ext: String) {
    GRADLE(".gradle.kts"),
    MAIN(".main.kts"),
    SPACE(".space.kts")
}

private fun containDotInName(fileName: String): Boolean = fileName.indexOf('.') != fileName.lastIndexOf('.')

internal class KotlinScriptFileTypeSchemaDetector : FileTypeUsageSchemaDescriptor {
    override fun describes(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".kts", true) && !containDotInName(file.name)
}

internal abstract class BaseKotlinScriptFileTypeSchemaDetector(val scriptType: KotlinScriptTypes) : FileTypeUsageSchemaDescriptor {
    override fun describes(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(scriptType.ext, true)
}

internal class KotlinGradleScriptFileTypeSchemaDetector : BaseKotlinScriptFileTypeSchemaDetector(KotlinScriptTypes.GRADLE)
internal class KotlinMainScriptFileTypeSchemaDetector : BaseKotlinScriptFileTypeSchemaDetector(KotlinScriptTypes.MAIN)
internal class KotlinSpaceScriptFileTypeSchemaDetector : BaseKotlinScriptFileTypeSchemaDetector(KotlinScriptTypes.SPACE)

/**
 * Can have false positive detections when dot is just used in file name,
 * but seems unlikely because it is an uncommon way of naming files in Kotlin
 * */
internal class KotlinCustomScriptFileTypeSchemaDetector : FileTypeUsageSchemaDescriptor {
    override fun describes(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".kts", true)
                && containDotInName(file.name)
                && !KotlinScriptTypes.values().any { type -> file.name.endsWith(type.ext) }
}