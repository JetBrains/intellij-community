// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.checkers.OptInNames

internal object MakeModuleOptInFix : KotlinSingleIntentionActionFactory() {

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val containingKtFile = diagnostic.psiElement.containingFile as? KtFile ?: return null
        val module = containingKtFile.module ?: return null
        val moduleDescriptor = module.toDescriptor()

        return AddModuleOptInFix(
            containingKtFile,
            module,
            OptInNames.REQUIRES_OPT_IN_FQ_NAME.takeIf {
                moduleDescriptor != null && OptInFixesUtils.annotationExists(moduleDescriptor, it)
            } ?: FqNames.OptInFqNames.OLD_EXPERIMENTAL_FQ_NAME
        )
    }
}

