// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class KotlinSdkExtension : BeforeAllCallback, AfterAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        KotlinSdkType.setUpIfNeeded()
    }

    override fun afterAll(context: ExtensionContext) {
        KotlinSdkType.removeKotlinSdkInTests()
    }
}