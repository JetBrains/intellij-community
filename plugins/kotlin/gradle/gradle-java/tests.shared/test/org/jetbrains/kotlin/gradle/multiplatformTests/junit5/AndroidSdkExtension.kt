// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class AndroidSdkExtension : AfterEachCallback {
    override fun afterEach(context: ExtensionContext?) {
        invokeAndWaitIfNeeded {
            runWriteAction {
                val sdkTable = ProjectJdkTable.getInstance()
                val androidSdks = sdkTable.allJdks.filter { sdk ->
                    sdk.getName().startsWith("Android ")
                }

                for (androidSdk in androidSdks) {
                    sdkTable.removeJdk(androidSdk)
                }
            }
        }
    }
}