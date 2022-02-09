// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.psi.KtFile


fun DeclarationDescriptor.isExcludedFromAutoImport(project: Project, inFile: KtFile?): Boolean {
    val fqName = importableFqName ?: return false
    return fqName.isExcludedFromAutoImport(project, inFile, inFile?.languageVersionSettings)
}
