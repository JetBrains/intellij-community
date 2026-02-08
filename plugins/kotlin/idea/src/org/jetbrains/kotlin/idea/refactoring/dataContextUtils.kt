// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
val DataContext.project: Project
    get() = CommonDataKeys.PROJECT.getData(this)!!

@K1Deprecation
val DataContext.hostEditor: Editor?
    get() = CommonDataKeys.HOST_EDITOR.getData(this)

@K1Deprecation
val DataContext.psiElement: PsiElement?
    get() = CommonDataKeys.PSI_ELEMENT.getData(this)