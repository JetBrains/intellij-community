// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("FunctionName", "RemoveExplicitTypeArguments")

package org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm

import org.gradle.api.artifacts.component.BuildIdentifier
import org.jetbrains.kotlin.idea.gradleTooling.reflect.ReflectionLogger
import org.jetbrains.kotlin.idea.gradleTooling.reflect.callReflective
import org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm.KotlinModuleIdentifierReflection.Companion.logger
import org.jetbrains.kotlin.idea.gradleTooling.reflect.parameters
import org.jetbrains.kotlin.idea.gradleTooling.reflect.returnType
import java.io.File

fun KotlinIdeFragmentDependencyReflection(dependency: Any): KotlinIdeFragmentDependencyReflection? {
    return when (dependency.javaClass.simpleName) {
        "IdeLocalSourceFragmentDependency" -> KotlinIdeLocalSourceFragmentDependencyReflectionImpl(dependency)
        "IdeMavenBinaryFragmentDependency" -> KotlinIdeMavenBinaryFragmentDependencyReflectionImpl(dependency)
        else -> {
            logger.logIssue("Unknown IdeFragmentDependency class: ${dependency.javaClass.simpleName}")
            null
        }
    }
}

sealed interface KotlinIdeFragmentDependencyReflection

interface KotlinIdeLocalSourceFragmentDependencyReflection : KotlinIdeFragmentDependencyReflection {
    val buildId: BuildIdentifier?
    val projectPath: String?
    val projectName: String?
    val kotlinModuleName: String?
    val kotlinFragmentName: String?
}

interface KotlinIdeMavenBinaryFragmentDependencyReflection : KotlinIdeFragmentDependencyReflection {
    val mavenGroup: String?
    val mavenModule: String?
    val version: String?
    val kotlinModuleName: String?
    val kotlinFragmentName: String?
    val files: List<File>?
}

private class KotlinIdeLocalSourceFragmentDependencyReflectionImpl(private val instance: Any) :
    KotlinIdeLocalSourceFragmentDependencyReflection {

    override val buildId: BuildIdentifier? by lazy {
        instance.callReflective("getBuildId", parameters(), returnType<BuildIdentifier>(), logger)
    }

    override val projectPath: String? by lazy {
        instance.callReflective("getProjectPath", parameters(), returnType<String>(), logger)
    }

    override val projectName: String? by lazy {
        instance.callReflective("getProjectName", parameters(), returnType<String>(), logger)
    }

    override val kotlinModuleName: String? by lazy {
        instance.callReflective("getKotlinModuleName", parameters(), returnType<String>(), logger)
    }

    override val kotlinFragmentName: String? by lazy {
        instance.callReflective("getKotlinFragmentName", parameters(), returnType<String>(), logger)
    }

    companion object {
        val logger = ReflectionLogger(KotlinIdeLocalSourceFragmentDependencyReflection::class.java)
    }
}

private class KotlinIdeMavenBinaryFragmentDependencyReflectionImpl(private val instance: Any) :
    KotlinIdeMavenBinaryFragmentDependencyReflection {
    override val mavenGroup: String? by lazy {
        instance.callReflective("getMavenGroup", parameters(), returnType<String>(), logger)
    }

    override val mavenModule: String? by lazy {
        instance.callReflective("getMavenModule", parameters(), returnType<String>(), logger)
    }
    override val version: String? by lazy {
        instance.callReflective("getVersion", parameters(), returnType<String>(), logger)
    }
    override val kotlinModuleName: String? by lazy {
        instance.callReflective("getKotlinModuleName", parameters(), returnType<String?>(), logger)
    }
    override val kotlinFragmentName: String? by lazy {
        instance.callReflective("getKotlinFragmentName", parameters(), returnType<String?>(), logger)
    }

    override val files: List<File>? by lazy {
        instance.callReflective("getFiles", parameters(), returnType<Iterable<File>>(), logger)?.toList()
    }

    companion object {
        val logger = ReflectionLogger(KotlinIdeMavenBinaryFragmentDependencyReflection::class.java)
    }
}
