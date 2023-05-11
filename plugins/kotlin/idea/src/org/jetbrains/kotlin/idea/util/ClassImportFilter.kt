/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtFile

/** Filter that can block classes from being automatically imported as a side-effect of other actions. */
fun interface ClassImportFilter {
    /** Returns whether to allow this class to be imported. */
    fun allowClassImport(descriptor: DeclarationDescriptor, contextFile: KtFile) : Boolean

    companion object {
        val EP_NAME = ExtensionPointName.create<ClassImportFilter>("org.jetbrains.kotlin.classImportFilter")
        fun allowClassImport(descriptor: DeclarationDescriptor, contextFile: KtFile) =
            EP_NAME.extensions.all { it.allowClassImport(descriptor, contextFile) }
    }
}