// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import com.intellij.gradle.toolingExtension.modelAction.GradleModelController
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleExecutionMode
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.gradle.idea.proto.generated.tcs.IdeaKotlinDependencyProtoKt
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

object IdeaMppProjectProvider : ProjectImportModelProvider {

    /**
     * Defines the `KotlinMPPGradleModel` class-path.
     *
     * Note: don't move it outside model provider. Gradle TAPI implicitly uses it during the `BuildAction` object traversal
     * for the BuildAction Gradle Daemon side's class-path resolution.
     *
     * The `BuildAction` Gradle Daemon side is the thin side which has only the Gradle TAPI and `GradleModelFetchAction` class-path.
     * I.e. on this side the `GradleVersion.current` returns Gradle TAPI version instead of Gradle project version.
     */
    val MODEL_CLASSPATH: Set<Class<*>> = setOf(
        KotlinMPPGradleModel::class.java,   // Module: intellij.kotlin.gradle.tooling.impl
        KotlinTarget::class.java,           // Module: intellij.kotlin.base.project-model
        IdeaKotlinDependency::class.java,           // Library: kotlin-gradle-plugin-idea
        IdeaKotlinDependencyProtoKt::class.java,    // Library: kotlin-gradle-plugin-idea-proto
        Extras::class.java                          // Library: kotlin-tooling-core
    )

    override fun populateModels(
        modelController: GradleModelController,
        buildModels: Collection<GradleBuild>,
        modelConsumer: GradleModelConsumer
    ) {
        modelController.fetchRequest(buildModels, KotlinMPPGradleModel::class.java)
            .executionMode(GradleExecutionMode.SEQUENTIAL)
            .execute(modelConsumer)
    }
}
