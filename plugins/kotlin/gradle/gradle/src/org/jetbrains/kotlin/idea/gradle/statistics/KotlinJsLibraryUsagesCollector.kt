// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics

/**
 * This collector collects usages of a whitelist of Kotlin/JS libraries being used in projects.
 *
 * The current implementation relies on the names of Gradle/Kotlin Multiplatform libraries that are being imported
 * into IDEA. If these names ever change, this collector will have to be adapted.
 */
class KotlinJsLibraryUsagesCollector : LibraryUsagesCollector(
    version = 1,
    eventGroupId = "kotlin.js.libraries",
    eventId = "used.js.library",
    librariesToScanFor = setOf(
        // Compose Multiplatform
        "org.jetbrains.compose.ui:ui-js",
        // Compose HTML
        "org.jetbrains.compose.html:html-core-js",
        // Kotlin-first HTML UI frameworks
        "dev.kilua:kilua-js",
        "com.varabyte.kobweb:kobweb-core-js",
        "codes.yousef:summon-js",
        "io.nacular.doodle:core-js",
        // Wrappers
        "org.jetbrains.kotlin-wrappers:kotlin-react-js",
        "org.jetbrains.kotlin-wrappers:kotlin-browser-js",
        "org.jetbrains.kotlin-wrappers:kotlin-node-js",
    )
)