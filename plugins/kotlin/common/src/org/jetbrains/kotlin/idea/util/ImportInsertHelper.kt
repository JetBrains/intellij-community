// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath
import java.util.*

abstract class ImportInsertHelper {
    /*TODO: implementation is not quite correct*/
    abstract fun isImportedWithDefault(importPath: ImportPath, contextFile: KtFile): Boolean

    abstract fun isImportedWithLowPriorityDefaultImport(importPath: ImportPath, contextFile: KtFile): Boolean

    abstract fun mayImportOnShortenReferences(descriptor: DeclarationDescriptor, contextFile: KtFile): Boolean

    abstract fun getImportSortComparator(contextFile: KtFile): Comparator<ImportPath>

    abstract fun importDescriptor(
        element: KtElement,
        descriptor: DeclarationDescriptor,
        actionRunningMode: ActionRunningMode = ActionRunningMode.RUN_IN_CURRENT_THREAD,
        forceAllUnderImport: Boolean = false
    ): ImportDescriptorResult

    fun importDescriptor(
        file: KtFile,
        descriptor: DeclarationDescriptor,
        forceAllUnderImport: Boolean = false
    ): ImportDescriptorResult = importDescriptor(
        file,
        descriptor,
        ActionRunningMode.RUN_IN_CURRENT_THREAD,
        forceAllUnderImport
    )

    abstract fun importPsiClass(
        element: KtElement,
        psiClass: PsiClass,
        actionRunningMode: ActionRunningMode = ActionRunningMode.RUN_IN_CURRENT_THREAD,
    ): ImportDescriptorResult

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ImportInsertHelper = project.getServiceSafe()
    }
}

enum class ImportDescriptorResult {
    FAIL,
    IMPORT_ADDED,
    ALREADY_IMPORTED
}

