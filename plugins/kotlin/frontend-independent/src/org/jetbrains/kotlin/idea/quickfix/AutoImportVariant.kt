// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.FqName
import javax.swing.Icon

interface AutoImportVariant {
    val hint: String
    val icon: Icon?
    val declarationToImport: PsiElement?
    val fqName: FqName

    val debugRepresentation: String
}