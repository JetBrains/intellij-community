// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.assignment.k1

import org.jetbrains.kotlin.assignment.plugin.diagnostics.ErrorsAssignmentPlugin
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes

class AssignmentPluginQuickFixContributor: QuickFixContributor {

    override fun registerQuickFixes(quickFixes: QuickFixes) {
        quickFixes.register(ErrorsAssignmentPlugin.NO_APPLICABLE_ASSIGN_METHOD, AssignmentPluginImportFix)
    }
}