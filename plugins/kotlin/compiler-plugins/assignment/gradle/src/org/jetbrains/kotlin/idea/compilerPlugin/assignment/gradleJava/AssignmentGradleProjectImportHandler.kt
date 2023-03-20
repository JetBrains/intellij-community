/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.compilerPlugin.assignment.gradleJava

import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.idea.gradleTooling.model.assignment.AssignmentModel

class AssignmentGradleProjectImportHandler : AbstractAnnotationBasedCompilerPluginGradleImportHandler<AssignmentModel>() {
    override val compilerPluginId = PLUGIN_ID
    override val pluginName = "assignment"
    override val annotationOptionName = ANNOTATION_OPTION_NAME
    override val pluginJarFileFromIdea = KotlinArtifacts.assignmentCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()
    override val modelKey = AssignmentProjectResolverExtension.KEY
}
