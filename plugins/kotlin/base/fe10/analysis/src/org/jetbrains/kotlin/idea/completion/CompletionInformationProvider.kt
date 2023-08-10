// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * The old package name left for compatibility reasons with Android IDE plugin
 * (it's bundled inside Android Studio).
 */
package org.jetbrains.kotlin.idea.completion

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor

interface CompletionInformationProvider {
    companion object {
        val EP_NAME: ExtensionPointName<CompletionInformationProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.completionInformationProvider")
    }

    fun getContainerAndReceiverInformation(descriptor: DeclarationDescriptor): String?
}