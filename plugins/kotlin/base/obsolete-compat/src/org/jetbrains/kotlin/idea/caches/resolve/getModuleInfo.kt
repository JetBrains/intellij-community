// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull

@Deprecated(
    "Use 'moduleInfoOrNull' instead",
    ReplaceWith("this.moduleInfoOrNull", "org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull")
)
fun PsiElement.getNullableModuleInfo(): IdeaModuleInfo? = this.moduleInfoOrNull