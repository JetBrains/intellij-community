// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

abstract class ImportInsertHelper {
    /*TODO: implementation is not quite correct*/
    abstract fun isImportedWithDefault(importPath: ImportPath, contextFile: KtFile): Boolean

    abstract fun isImportedWithLowPriorityDefaultImport(importPath: ImportPath, contextFile: KtFile): Boolean

    abstract fun mayImportOnShortenReferences(descriptor: DeclarationDescriptor, contextFile: KtFile): Boolean

    abstract fun importDescriptor(
        element: KtElement,
        descriptor: DeclarationDescriptor,
        runImmediately: Boolean = true,
        forceAllUnderImport: Boolean = false,
        aliasName: Name? = null,
    ): ImportDescriptorResult

    fun importDescriptor(file: KtFile, descriptor: DeclarationDescriptor, forceAllUnderImport: Boolean = false): ImportDescriptorResult {
        return importDescriptor(file as KtElement, descriptor, runImmediately = true, forceAllUnderImport)
    }

    abstract fun importPsiClass(element: KtElement, psiClass: PsiClass, runImmediately: Boolean = true): ImportDescriptorResult

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ImportInsertHelper = project.service()
    }
}

enum class ImportDescriptorResult {
    FAIL,
    IMPORT_ADDED,
    ALREADY_IMPORTED
}

@Suppress("unused")
@Deprecated("Use `runImmediately` flag instead.")
enum class ActionRunningMode {
    RUN_IN_CURRENT_THREAD,
    RUN_IN_EDT
}