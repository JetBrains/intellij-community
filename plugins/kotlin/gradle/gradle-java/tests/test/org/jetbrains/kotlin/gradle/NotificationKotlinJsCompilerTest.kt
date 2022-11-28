// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.kotlin.idea.KotlinIdeaBundle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class NotificationKotlinJsCompilerTest : MultiplePluginVersionGradleImportingTestCase() {
    override fun setUpProjectRoot() {
        val projectDir = File(myProject.basePath!!)
        FileUtil.ensureExists(projectDir)
        myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.7.0+")
    fun testNotificationJsCompiler() {
        configureByFiles()
        linkProject(myProject.basePath!!)

        val notificationText = KotlinBundle.message(
            "notification.text.kotlin.js.compiler.body",
        )
        val myDisposable = Disposer.newDisposable()
        val notificationFlag = AtomicBoolean(false)
        try {
            val connection = myProject.messageBus.connect(myDisposable)
            connection.subscribe(Notifications.TOPIC, object : Notifications {
                override fun notify(notification: Notification) {
                    if (notificationText == notification.content) {
                        notificationFlag.set(true)
                    }
                }
            })
            importProject()
        } finally {
            Disposer.dispose(myDisposable)
        }

        assertTrue(notificationFlag.get())
    }
}
