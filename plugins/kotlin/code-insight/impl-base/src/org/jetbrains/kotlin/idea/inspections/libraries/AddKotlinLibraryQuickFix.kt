// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.libraries

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinDependencyProvider
import org.jetbrains.kotlin.idea.configuration.isProjectSyncPendingOrInProgress

class AddKotlinLibraryQuickFix(
    private val dependencyManager: KotlinBuildSystemDependencyManager,
    private val libraryDescriptor: ExternalLibraryDescriptor,
    @IntentionName
    private val quickFixText: String
) : ModCommandAction, HighPriorityAction {
    override fun getPresentation(context: ActionContext): Presentation? {
        val file = context.file
        val module = file.module ?: return null
        return quickFixText
            .takeIf { dependencyManager.isApplicable(module) && !dependencyManager.isProjectSyncPendingOrInProgress() }
            ?.let(Presentation::of)
    }
    override fun getFamilyName(): String = quickFixText

    override fun perform(context: ActionContext): ModCommand {
        val file = context.file
        val module = file.module ?: return ModCommand.nop()
        val element = file
            .takeIf { dependencyManager.isApplicable(module) && !dependencyManager.isProjectSyncPendingOrInProgress() } ?:
            return ModCommand.nop()

        val addDependencyModCommand =
            dependencyManager.addDependencyModCommand(file, module, libraryDescriptor)

        return if (addDependencyModCommand != ModCommand.nop()) {
            addDependencyModCommand
        } else {
            KotlinDependencyProvider.addLibraryModCommand(element, libraryDescriptor)
        }
    }
}