// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project

interface KotlinUniversalQuickFix : IntentionActionWithFixAllOption, LocalQuickFix {
    @JvmDefault
    override fun getName() = text

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        invoke(project, null, descriptor.psiElement?.containingFile)
    }
}