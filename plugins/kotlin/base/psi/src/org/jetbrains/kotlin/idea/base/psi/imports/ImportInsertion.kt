// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi.imports

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.nextLeaf
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

fun addImport(project: Project, file: KtFile, fqName: FqName, allUnder: Boolean = false, alias: Name? = null): KtImportDirective {
    val importPath = ImportPath(fqName, allUnder, alias)

    val psiFactory = KtPsiFactory(project)
    if (file is KtCodeFragment) {
        val newDirective = psiFactory.createImportDirective(importPath)
        file.addImportsFromString(newDirective.text)
        return newDirective
    }

    val importList = file.importList
    if (importList != null) {
        val newDirective = psiFactory.createImportDirective(importPath)
        val imports = importList.imports
        return if (imports.isEmpty()) {
            val packageDirective = file.packageDirective?.takeIf { it.packageKeyword != null }
            packageDirective?.let {
                file.addAfter(psiFactory.createNewLine(2), it)
            }

            (importList.add(newDirective) as KtImportDirective).also {
                if (packageDirective == null) {
                    val whiteSpace = importList.nextLeaf(true)
                    if (whiteSpace is PsiWhiteSpace) {
                        val newLineBreak = if (whiteSpace.isMultiLine()) {
                            psiFactory.createWhiteSpace("\n" + whiteSpace.text)
                        } else {
                            psiFactory.createWhiteSpace("\n\n" + whiteSpace.text)
                        }

                        whiteSpace.replace(newLineBreak)
                    } else {
                        file.addAfter(psiFactory.createNewLine(2), importList)
                    }
                }
            }
        } else {

            val importPathComparator = KotlinImportPathComparator.create(file)
            val insertAfter = imports.lastOrNull {
                val directivePath = it.importPath
                directivePath != null && importPathComparator.compare(directivePath, importPath) <= 0
            }

            (importList.addAfter(newDirective, insertAfter) as KtImportDirective).also {
                importList.addBefore(psiFactory.createNewLine(1), it)
            }
        }
    } else {
        error("Trying to insert import $fqName into a file ${file.name} of type ${file::class.java} with no import list.")
    }
}
