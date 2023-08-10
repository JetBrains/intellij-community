// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.regex.Pattern
import org.jetbrains.kotlin.analysis.low.level.api.fir.project.structure.LLFirBuiltinsSessionFactory
import org.jetbrains.kotlin.idea.base.analysisApiProviders.KotlinModuleStateTrackerProvider

private val STDLIB_PATTERN = Pattern.compile("kotlin-stdlib-(\\d*)\\.(\\d*)\\.(\\d*)\\.jar")

class FirIdeKotlinModuleStateTrackerProvider(project: Project) : KotlinModuleStateTrackerProvider(project) {
    override fun shouldInvalidateBuiltinSession(events: List<VFileEvent>): Boolean {
        return events.find { event ->
            event is VFileContentChangeEvent && STDLIB_PATTERN.matcher(event.file.name).matches()
        } != null
    }
}