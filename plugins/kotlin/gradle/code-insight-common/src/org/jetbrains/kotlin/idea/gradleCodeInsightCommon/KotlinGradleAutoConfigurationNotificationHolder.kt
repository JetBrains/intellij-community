// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.configuration.KotlinAutoConfigurationNotificationHolder
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.native.KotlinGradleCodeInsightCommonBundle

@Service(Service.Level.PROJECT)
internal class KotlinGradleAutoConfigurationNotificationHolder(project: Project) : KotlinAutoConfigurationNotificationHolder(project) {

    override val buildSystemDocumentationUrl: String =
        KotlinGradleCodeInsightCommonBundle.message("auto.configure.kotlin.documentation.gradle.url")
    override val buildSystemName: String = KotlinGradleCodeInsightCommonBundle.message("gradle.name")

    companion object {
        fun getInstance(project: Project): KotlinGradleAutoConfigurationNotificationHolder {
            return project.service()
        }
    }
}