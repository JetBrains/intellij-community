/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.name.FqNameUnsafe

interface NotPropertiesService {

    fun getNotProperties(element: PsiElement): Set<FqNameUnsafe>

    companion object {
        fun getInstance(project: Project): NotPropertiesService = project.getServiceSafe()

        fun getNotProperties(element: PsiElement) = getInstance(element.project).getNotProperties(element)
    }
}