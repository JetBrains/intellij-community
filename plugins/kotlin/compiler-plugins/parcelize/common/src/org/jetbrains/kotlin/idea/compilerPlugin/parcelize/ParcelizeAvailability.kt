// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.util.module

object ParcelizeAvailability {
    fun isAvailable(element: PsiElement): Boolean {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return true
        }

        val module = element.module ?: return false
        return isAvailable(module)
    }

    fun isAvailable(module: Module): Boolean {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return true
        }

        return ParcelizeAvailabilityProvider.PROVIDER_EP.getExtensions(module.project).any { it.isAvailable(module) }
    }
}

interface ParcelizeAvailabilityProvider {
    companion object {
        val PROVIDER_EP: ExtensionPointName<ParcelizeAvailabilityProvider> =
            ExtensionPointName("org.jetbrains.kotlin.idea.compilerPlugin.parcelize.availabilityProvider")
    }

    fun isAvailable(module: Module): Boolean
}