// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("FunctionName", "RemoveExplicitTypeArguments")

package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinModuleIdentifierReflection.Companion.logger

fun KotlinModuleIdentifierReflection(moduleIdentifier: Any): KotlinModuleIdentifierReflection? {
    return when (moduleIdentifier.javaClass.name) {
        "org.jetbrains.kotlin.project.model.LocalModuleIdentifier" ->
            KotlinLocalModuleIdentifierReflectionImpl(moduleIdentifier)
        "org.jetbrains.kotlin.project.model.MavenModuleIdentifier" ->
            KotlinMavenModuleIdentifierReflectionImpl(moduleIdentifier)
        else -> {
            logger.logIssue("Unknown module identifier: \"${moduleIdentifier.javaClass.name}\"")
            null
        }
    }
}

interface KotlinModuleIdentifierReflection {
    val moduleClassifier: String?

    companion object {
        internal val logger = ReflectionLogger(KotlinModuleReflection::class.java)
    }
}

interface KotlinLocalModuleIdentifierReflection : KotlinModuleIdentifierReflection {
    val buildId: String?
    val projectId: String?
}

interface KotlinMavenModuleIdentifierReflection : KotlinModuleIdentifierReflection {
    val group: String?
    val name: String?
}

private class KotlinModuleIdentifierReflectionImpl(
    private val instance: Any
) : KotlinModuleIdentifierReflection {
    override val moduleClassifier: String? by lazy {
        instance.callReflective("getModuleClassifier", parameters(), returnType<String?>(), logger)
    }
}

private class KotlinLocalModuleIdentifierReflectionImpl(
    private val instance: Any
) : KotlinLocalModuleIdentifierReflection,
    KotlinModuleIdentifierReflection by KotlinModuleIdentifierReflectionImpl(instance) {

    override val buildId: String? by lazy {
        instance.callReflectiveGetter("getBuildId", logger)
    }
    override val projectId: String? by lazy {
        instance.callReflectiveGetter("getProjectId", logger)
    }
}

private class KotlinMavenModuleIdentifierReflectionImpl(
    private val instance: Any
) : KotlinMavenModuleIdentifierReflection,
    KotlinModuleIdentifierReflection by KotlinModuleIdentifierReflectionImpl(instance) {

    override val group: String? by lazy {
        instance.callReflectiveGetter("getGroup", logger)
    }
    override val name: String? by lazy {
        instance.callReflectiveGetter("getName", logger)
    }
}
