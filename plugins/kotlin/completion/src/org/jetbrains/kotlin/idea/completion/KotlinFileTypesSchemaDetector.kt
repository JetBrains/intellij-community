// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class KotlinFileTypeSchemaDetector : FileTypeUsageSchemaDescriptor {
    override fun describes(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".kt")
}

class KotlinScriptFileTypeSchemaDetector : FileTypeUsageSchemaDescriptor {
    override fun describes(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".kts") && !file.name.endsWith(".gradle.kts")
}