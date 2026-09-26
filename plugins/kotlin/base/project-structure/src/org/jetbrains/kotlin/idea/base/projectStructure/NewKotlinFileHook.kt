// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
abstract class NewKotlinFileHook {
    companion object {
        val EP_NAME: ExtensionPointName<NewKotlinFileHook> =
            ExtensionPointName.create("org.jetbrains.kotlin.newFileHook")

        fun runPostProcessHooks(file: KtFile) {
            val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return
            for (hook in EP_NAME.extensionList) {
                hook.postProcess(file, module)
            }
        }
    }

    abstract fun postProcess(createdElement: KtFile, module: Module)
}