// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

interface KotlinBrowserTestExtensions : Serializable {
    // Null means that JS browser test dsl was not used
    val browserTestRunners: Set<BrowserTestRunner>?
}

interface BrowserTestRunner : Serializable {
    val name: String
    val type: BrowserTestRunnerType
    val testTaskName: String
    /** Tells if IDEA can initiate debug session with Gradle task using `org.jetbrains.kotlin:kotlin-gradle-plugin-debug-idea` library */
    val supportsKotlinBrowserDebugProtocolVersion: KotlinBrowserDebugProtocolVersion
}

enum class BrowserTestRunnerType {
    CHROMIUM,
    FIREFOX,
    WEBKIT,
}

/**
 * Represents versions of Kotlin where protocol feature was introduced or updated.
 */
enum class KotlinBrowserDebugProtocolVersion {
    UNSUPPORTED,
    V2_5,
}
