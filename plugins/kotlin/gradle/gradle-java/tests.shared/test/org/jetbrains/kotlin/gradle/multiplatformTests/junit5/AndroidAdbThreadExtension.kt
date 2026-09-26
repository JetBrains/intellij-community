// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.util.application
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class AndroidAdbThreadExtension: BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        ThreadLeakTracker.longRunningThreadCreated(
            application,
            "AndroidAdbSessionHost",
            "InnocuousThread-",
        )
    }
}