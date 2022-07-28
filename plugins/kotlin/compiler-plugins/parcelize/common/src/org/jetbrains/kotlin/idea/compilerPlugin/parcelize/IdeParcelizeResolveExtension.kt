// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.parcelize.ParcelizeResolveExtension

class IdeParcelizeResolveExtension : ParcelizeResolveExtension() {
    override fun isAvailable(element: PsiElement): Boolean {
        return ParcelizeAvailability.isAvailable(element)
    }
}