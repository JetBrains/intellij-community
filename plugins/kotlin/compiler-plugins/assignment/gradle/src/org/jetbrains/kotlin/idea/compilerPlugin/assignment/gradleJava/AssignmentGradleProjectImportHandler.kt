/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.compilerPlugin.assignment.gradleJava

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractGradleImportHandler
import java.nio.file.Path

class AssignmentGradleProjectImportHandler : AbstractGradleImportHandler() {

    override val pluginJarsRegex: List<Regex> = listOf("$ASSIGNMENT_COMPILER_PLUGIN_JAR_NAME-.*\\.jar".toRegex())
    override val replacedJar: Path = KotlinArtifacts.assignmentCompilerPluginPath

    companion object {
        private const val ASSIGNMENT_COMPILER_PLUGIN_JAR_NAME = "kotlin-assignment-compiler-plugin-embeddable"
    }

}
