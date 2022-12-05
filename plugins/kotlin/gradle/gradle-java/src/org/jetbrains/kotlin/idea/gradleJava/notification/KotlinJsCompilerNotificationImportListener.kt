// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.notification

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.DoNotShowAgainNotificationAction
import org.jetbrains.kotlin.idea.util.KotlinJsCompilerNotificationImportListener213ClassLoaderHack
import java.util.concurrent.Callable

const val IGNORE_KOTLIN_JS_COMPILER_NOTIFICATION = "notification.kotlin.js.compiler.ignored"
const val KOTLIN_JS_COMPILER_SHOULD_BE_NOTIFIED = "notification.kotlin.js.compiler.should.be.notified"

class KotlinJsCompilerNotificationImportListener(private val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
        KotlinJsCompilerNotificationImportListener213ClassLoaderHack.onImportFinished(project)
    }
}
