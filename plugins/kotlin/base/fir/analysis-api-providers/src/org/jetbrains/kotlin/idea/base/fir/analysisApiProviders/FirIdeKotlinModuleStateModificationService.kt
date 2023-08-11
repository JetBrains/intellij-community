// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.kotlin.idea.base.analysisApiProviders.KotlinModuleStateModificationService
import java.util.regex.Pattern

private val STDLIB_PATTERN = Pattern.compile("kotlin-stdlib-(\\d*)\\.(\\d*)\\.(\\d*)\\.jar")

class FirIdeKotlinModuleStateModificationService(project: Project) : KotlinModuleStateModificationService(project) {
    override fun mayBuiltinsHaveChanged(events: List<VFileEvent>): Boolean {
        return events.find { event ->
            event is VFileContentChangeEvent && STDLIB_PATTERN.matcher(event.file.name).matches()
        } != null
    }
}
