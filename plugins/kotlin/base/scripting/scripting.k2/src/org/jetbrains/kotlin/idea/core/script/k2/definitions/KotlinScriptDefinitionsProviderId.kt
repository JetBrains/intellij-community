// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

enum class KotlinScriptDefinitionsProviderId(val id: String) {
    FROM_DEPENDENCIES("FromDependencies"),
    GRADLE("Gradle"),
    KOTLIN_SCRATCH("KotlinScratch"),
    MAIN_KTS("MainKts"),
}
