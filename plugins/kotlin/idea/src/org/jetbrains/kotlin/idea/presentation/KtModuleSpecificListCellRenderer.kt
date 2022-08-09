// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.presentation

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.NavigatablePsiElement
import org.jetbrains.kotlin.idea.base.util.module

abstract class KtModuleSpecificListCellRenderer<T : NavigatablePsiElement> : PsiElementListCellRenderer<T>() {

    override fun getComparingObject(element: T?): Comparable<Nothing> {
        val baseText = super.getComparingObject(element)
        val moduleName = runReadAction {
            element?.module?.name
        } ?: return baseText
        return "$baseText [$moduleName]"
    }
}