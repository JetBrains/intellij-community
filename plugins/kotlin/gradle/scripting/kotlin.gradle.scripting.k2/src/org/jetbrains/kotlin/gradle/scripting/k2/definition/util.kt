// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.definition

import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.script.v1.kotlinScriptTemplateInfo
import kotlin.script.experimental.api.ide


fun GradleScriptDefinition.withIdeKeys(): GradleScriptDefinition = with {
    ide {
        kotlinScriptTemplateInfo {
            id = "gradle-kts"
            title = ".gradle.kts"
            templateName = "Kotlin Script Gradle"
            description = KotlinBundle.message("action.new.script.description.gradle.kts")
        }
    }
}