// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.notifications

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.idea.gradle.ui.IsResolveModulePerSourceSetSetting
import org.jetbrains.kotlin.idea.gradle.ui.KOTLIN_UPDATE_IS_RESOLVE_MODULE_PER_SOURCE_SET_GROUP_ID
import org.jetbrains.kotlin.idea.gradle.ui.SuppressResolveModulePerSourceSetNotificationState
import org.jetbrains.kotlin.idea.gradle.ui.notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.AssumptionViolatedException
import java.util.*

class LegacyIsResolveModulePerSourceSetNotificationTest : LightPlatformTestCase() {

    private val notifications: MutableList<Notification> = Collections.synchronizedList(mutableListOf())

    private fun getDataContext(notification: Notification): DataContext = DataContext { id ->
        when {
            CommonDataKeys.PROJECT.`is`(id) -> project
            Notification.KEY.`is`(id) -> notification
            else -> null
        }
    }

    override fun setUp() {
        super.setUp()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                if (notification.groupId == KOTLIN_UPDATE_IS_RESOLVE_MODULE_PER_SOURCE_SET_GROUP_ID) {
                    notifications += notification
                }
            }
        })
        notifications.clear()
    }

    override fun tearDown() {
        try {
            GradleSettings.getInstance(project).apply {
                linkedProjectsSettings.forEach { projectSetting ->
                    unlinkExternalProject(projectSetting.externalProjectPath)
                }
            }
            notifications.forEach { it.expire() }
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun `test shows notification when isResolveModulePerSourceSet is false`() {
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
            project,
            notificationSuppressState = TestNotificationSuppressState(isSuppressed = false),
            isResolveModulePerSourceSetSetting = TestIsResolveModulePerSourceSetSetting(isResolveModulePerSourceSet = false)
        )

        assertEquals(
            "Expect a single notification being published",
            1, notifications.size
        )

        assertEquals(
            "Expected notification with exactly two actions",
            2, notifications.single().actions.size
        )
    }

    fun `test does not show any notification when isResolveModulePerSourceSet is true`() {
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
            project,
            notificationSuppressState = TestNotificationSuppressState(isSuppressed = false),
            isResolveModulePerSourceSetSetting = TestIsResolveModulePerSourceSetSetting(isResolveModulePerSourceSet = true)
        )

        assertEquals(
            "Expected no notification being published",
            0, notifications.size
        )
    }

    fun `test does not show any notification when notification is suppressed`() {
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
            project,
            notificationSuppressState = TestNotificationSuppressState(isSuppressed = true),
            isResolveModulePerSourceSetSetting = TestIsResolveModulePerSourceSetSetting(isResolveModulePerSourceSet = false)
        )

        assertEquals(
            "Expected no notification being published",
            0, notifications.size
        )
    }


    fun `test first notification action will update setting`() {
        val isResolveModulePerSourceSetSetting = TestIsResolveModulePerSourceSetSetting(isResolveModulePerSourceSet = false)
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
            project,
            notificationSuppressState = TestNotificationSuppressState(isSuppressed = false),
            isResolveModulePerSourceSetSetting = isResolveModulePerSourceSetSetting
        )

        val notification = notifications.single()
        val firstAction = notification.actions.firstOrNull() ?: throw AssumptionViolatedException("No notification actions present")

        assertFalse(
            "Expected 'isResolveModulePerSourceSet still being false before action",
            isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet
        )

        Notification.fire(notification, firstAction, getDataContext(notification))

        assertTrue(
            "Expected 'isResolveModulePerSourceSet' being set to true by action",
            isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet
        )
    }

    fun `test second notification action will suppress notification`() {
        val notificationSuppressState = TestNotificationSuppressState(isSuppressed = false)
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
            project,
            notificationSuppressState = notificationSuppressState,
            isResolveModulePerSourceSetSetting = TestIsResolveModulePerSourceSetSetting(isResolveModulePerSourceSet = false)
        )

        val notification = notifications.single()
        val firstAction = notification.actions.getOrNull(1) ?: return  /* Covered by other test */

        assertFalse(
            "Expected notification not being suppressed before action",
            notificationSuppressState.isSuppressed
        )

        Notification.fire(notification, firstAction, getDataContext(notification))

        assertTrue(
            "Expected notification being suppressed after action",
            notificationSuppressState.isSuppressed
        )
    }

    fun `test default IsResolveModulePerSourceSetSetting is reflecting GradleProjectSettings`() {
        val isResolveModulePerSourceSetSetting = IsResolveModulePerSourceSetSetting.default(project)

        val gradleProjectSettings = GradleProjectSettings().apply {
            externalProjectPath = project.basePath
        }

        val gradleSettings = GradleSettings.getInstance(project)
        gradleSettings.linkProject(gradleProjectSettings)

        assertEquals(
            "Expected exactly least one linked GradleProjectSetting",
            1, gradleSettings.linkedProjectsSettings.size
        )

        /* Assert that isResolveModulePerSourceSetSetting reflects changes to GradleProjectSettings */
        gradleProjectSettings.isResolveModulePerSourceSet = true
        assertTrue(
            "Expected isResolveModulePerSourceSetSetting reflecting latest state of GradleProjectSetting",
            isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet
        )

        gradleProjectSettings.isResolveModulePerSourceSet = false
        assertFalse(
            "Expected isResolveModulePerSourceSetSetting reflecting latest state of GradleProjectSetting",
            isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet
        )

        /* Assert that GradleProjectSettings reflects changes to isResolveModulePerSourceSetSetting  */
        isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet = true
        assertTrue(
            "Expected GradleProjectSetting reflecting latest state of isResolveModulePerSourceSetSetting",
            gradleSettings.linkedProjectsSettings.single().isResolveModulePerSourceSet
        )

        isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet = false
        assertFalse(
            "Expected GradleProjectSetting reflecting latest state of isResolveModulePerSourceSetSetting",
            gradleSettings.linkedProjectsSettings.single().isResolveModulePerSourceSet
        )
    }

    fun `test default IsResolveModulePerSourceSetSetting when no GradleProjectSetting is present`() {
        val isResolveModulePerSourceSetSetting = IsResolveModulePerSourceSetSetting.default(project)
        assertEquals(
            "Expected no linked GradleProjectSettings",
            0, GradleSettings.getInstance(project).linkedProjectsSettings.size
        )

        assertTrue(
            "IsResolveModulePerSourceSetSetting.default to handle missing GradleProjectSettings as if isResolveModulePerSourceSet=true",
            isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet
        )
    }

    fun `test default SuppressResolveModulePerSourceSetNotificationState`() {
        val firstStateInstance = SuppressResolveModulePerSourceSetNotificationState.default(project)
        val secondStateInstance = SuppressResolveModulePerSourceSetNotificationState.default(project)

        firstStateInstance.isSuppressed = true
        assertTrue(
            "Expected secondStateInstance reflecting changes of firstStateInstance",
            secondStateInstance.isSuppressed
        )

        firstStateInstance.isSuppressed = false
        assertFalse(
            "Expected secondStateInstance reflecting changes of firstStateInstance",
            secondStateInstance.isSuppressed
        )

        secondStateInstance.isSuppressed = true
        assertTrue(
            "Expected firstStateInstance reflecting changes of secondStateInstance",
            firstStateInstance.isSuppressed
        )

        secondStateInstance.isSuppressed = false
        assertFalse(
            "Expected firstStateInstance reflecting changes of secondStateInstance",
            firstStateInstance.isSuppressed
        )

        firstStateInstance.isSuppressed = true
        assertTrue(
            "Expected new state instance reflecting current state",
            SuppressResolveModulePerSourceSetNotificationState.default(project).isSuppressed
        )

        firstStateInstance.isSuppressed = false
        assertFalse(
            "Expected new state instance reflecting current state",
            SuppressResolveModulePerSourceSetNotificationState.default(project).isSuppressed
        )
    }

    fun `test notification is just shown once`() {
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
            project,
            notificationSuppressState = TestNotificationSuppressState(isSuppressed = false),
            isResolveModulePerSourceSetSetting = TestIsResolveModulePerSourceSetSetting(isResolveModulePerSourceSet = false)
        )

        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
            project,
            notificationSuppressState = TestNotificationSuppressState(isSuppressed = false),
            isResolveModulePerSourceSetSetting = TestIsResolveModulePerSourceSetSetting(isResolveModulePerSourceSet = false)
        )

        assertEquals(
            "Expected exactly one notification fired, because first invocation is not expired yet",
            1, notifications.size
        )

        notifications.single().expire()

        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
            project,
            notificationSuppressState = TestNotificationSuppressState(isSuppressed = false),
            isResolveModulePerSourceSetSetting = TestIsResolveModulePerSourceSetSetting(isResolveModulePerSourceSet = false)
        )

        assertEquals(
            "Expected new notification being created because first notification was expired",
            2, notifications.size
        )

    }
}


private data class TestNotificationSuppressState(
    override var isSuppressed: Boolean
) : SuppressResolveModulePerSourceSetNotificationState

private data class TestIsResolveModulePerSourceSetSetting(
    override var isResolveModulePerSourceSet: Boolean
) : IsResolveModulePerSourceSetSetting
