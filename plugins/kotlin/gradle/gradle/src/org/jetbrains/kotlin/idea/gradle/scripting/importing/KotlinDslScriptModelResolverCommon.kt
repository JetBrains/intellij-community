// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.scripting.importing

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Pair
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters.*
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptAdditionalTask
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@IntellijInternalApi
val LOG = Logger.getInstance(KotlinDslScriptModelResolverCommon::class.java)

@Order(Integer.MIN_VALUE) // to be the first
abstract class KotlinDslScriptModelResolverCommon : AbstractProjectResolverExtension() {
    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return setOf(KotlinDslScriptsModel::class.java)
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(KotlinDslScriptAdditionalTask::class.java)
    }

    override fun getExtraCommandLineArgs(): List<String> {
        return listOf("-P$CORRELATION_ID_GRADLE_PROPERTY_NAME=${System.nanoTime()}")
    }

    override fun getExtraJvmArgs(): List<Pair<String, String>> {
        return if (GradleVersion.current() >= GradleVersion.version("7.6")) {
            listOf(
                Pair(
                    PROVIDER_MODE_SYSTEM_PROPERTY_NAME,
                    CLASSPATH_MODE_SYSTEM_PROPERTY_VALUE
                )
            )
        } else {
            super.extraJvmArgs
        }
    }
}
