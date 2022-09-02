// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.valueContainerAssignment.gradleJava

import org.jetbrains.kotlin.container.assignment.ValueContainerAssignmentPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.container.assignment.ValueContainerAssignmentPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup
import org.jetbrains.kotlin.idea.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.idea.gradleTooling.model.valueContainerAssignment.ValueContainerAssignmentModel

class ValueContainerAssignmentGradleProjectImportHandler : AbstractAnnotationBasedCompilerPluginGradleImportHandler<ValueContainerAssignmentModel>() {
    override val compilerPluginId = PLUGIN_ID
    override val pluginName = "value-container-assignment"
    override val annotationOptionName = ANNOTATION_OPTION_NAME
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.valueContainerAssignmentCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()
    override val modelKey = ValueContainerAssignmentProjectResolverExtension.KEY
}
