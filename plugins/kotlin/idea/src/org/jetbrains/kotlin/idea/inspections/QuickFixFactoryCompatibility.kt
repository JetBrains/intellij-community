// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.openapi.project.Project

fun createAddToDependencyInjectionAnnotationsFix(project: Project, fqName: String): IntentionAction {
    return QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, fqName)
}