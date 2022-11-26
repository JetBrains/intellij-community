// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinElementDescriptionProviderBase
import org.jetbrains.kotlin.idea.refactoring.rename.RenameJavaSyntheticPropertyHandler
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinPropertyProcessor

class KotlinElementDescriptionProvider : KotlinElementDescriptionProviderBase() {

    override val PsiElement.isRenameJavaSyntheticPropertyHandler: Boolean
        get() = this is RenameJavaSyntheticPropertyHandler.SyntheticPropertyWrapper

    override val PsiElement.isRenameKotlinPropertyProcessor: Boolean
        get() = this is RenameKotlinPropertyProcessor.PropertyMethodWrapper
}
