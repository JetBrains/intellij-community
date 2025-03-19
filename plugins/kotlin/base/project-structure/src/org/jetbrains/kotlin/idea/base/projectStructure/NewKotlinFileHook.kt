// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
abstract class NewKotlinFileHook {
    companion object {
        val EP_NAME: ExtensionPointName<NewKotlinFileHook> =
            ExtensionPointName.create("org.jetbrains.kotlin.newFileHook")
    }

    abstract fun postProcess(createdElement: KtFile, module: Module)
}