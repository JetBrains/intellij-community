// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.notification.impl.NotificationIdsHolder

class KotlinGradleNotificationIdsHolder : NotificationIdsHolder {
    companion object {
        const val kotlinScriptingJvmInvalid = "gradle.kotlin.scripting.jvm.invalid"
    }

    override fun getNotificationIds(): List<String> {
        return listOf(kotlinScriptingJvmInvalid)
    }
}
