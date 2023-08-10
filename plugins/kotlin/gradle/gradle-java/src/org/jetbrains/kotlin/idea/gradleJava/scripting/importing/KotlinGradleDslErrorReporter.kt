// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting.importing

import com.intellij.build.FilePosition
import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemEventDispatcher
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import java.io.File

@Nls
private val gradle_build_script_errors_group = KotlinIdeaGradleBundle.message("kotlin.build.script.errors")

class KotlinGradleDslErrorReporter(
    project: Project,
    private val task: ExternalSystemTaskId
) {
    private val syncViewManager = project.service<SyncViewManager>()
    private val buildEventDispatcher = ExternalSystemEventDispatcher(task, syncViewManager)

    fun reportError(
        scriptFile: File,
        model: KotlinDslScriptModel
    ) {
        model.messages.forEach {
            val severity = when (it.severity) {
                KotlinDslScriptModel.Severity.WARNING -> MessageEvent.Kind.WARNING
                KotlinDslScriptModel.Severity.ERROR -> MessageEvent.Kind.ERROR
            }
            val position = it.position
            if (position == null) {
                buildEventDispatcher.onEvent(
                    task,
                    MessageEventImpl(
                        task,
                        severity,
                        gradle_build_script_errors_group,
                        it.text,
                        it.details
                    )
                )
            } else {
                buildEventDispatcher.onEvent(
                    task,
                    FileMessageEventImpl(
                        task,
                        severity,
                        gradle_build_script_errors_group,
                        it.text, it.details,
                        // 0-based line numbers
                        FilePosition(scriptFile, position.line - 1, position.column)
                    ),
                )
            }
        }
    }

    companion object {
        @IntellijInternalApi
        val build_script_errors_group: String
            @TestOnly
            get() = gradle_build_script_errors_group
    }
}
