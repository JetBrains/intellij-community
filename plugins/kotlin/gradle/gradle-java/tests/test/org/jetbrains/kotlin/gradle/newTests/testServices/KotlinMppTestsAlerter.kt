
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import com.intellij.openapi.diagnostic.Logger

internal interface KotlinMppTestsAlerter {
    fun alert(message: String)

    companion object {
        val logger = Logger.getInstance("org.jetbrains.kotlin.multiplatform.tests")

        fun getInstance(): KotlinMppTestsAlerter = LogAlerter // TODO: use OTel on CI
    }

    object LogAlerter : KotlinMppTestsAlerter {
        override fun alert(message: String) {
            logger.warn("Alert!\n$message")
        }
    }
}
