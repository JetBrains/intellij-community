// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi.imports

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.nextLeaf
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * @return newly added import if it's not present in the file, and already existing import, otherwise.
 */
fun KtFile.addImport(fqName: FqName, allUnder: Boolean = false, alias: Name? = null, project: Project = this.project): KtImportDirective {
    val importPath = ImportPath(fqName, allUnder, alias)

    val psiFactory = KtPsiFactory(project)
    if (this is KtCodeFragment) {
        val newDirective = psiFactory.createImportDirective(importPath)
        addImportsFromString(newDirective.text)
        return newDirective
    }

    val importList = importList
    if (importList != null) {
        val newDirective = psiFactory.createImportDirective(importPath)
        val imports = importList.imports
        return if (imports.isEmpty()) {
            val packageDirective = packageDirective?.takeIf { it.packageKeyword != null }
            packageDirective?.let {
                val elemAfterPkg = packageDirective.nextSibling
                val linesAfterPkg = elemAfterPkg.getLineCount() - 1
                val missingLines = 2 - linesAfterPkg
                if (missingLines > 0) addAfter(psiFactory.createNewLine(missingLines), it)
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
                        addAfter(psiFactory.createNewLine(2), importList)
                    }
                }
            }
        } else {

            val importPathComparator = KotlinImportPathComparator.create(this)
            val insertAfter = imports.lastOrNull {
                val directivePath = it.importPath
                directivePath != null && importPathComparator.compare(directivePath, importPath) <= 0
            }

            if (insertAfter != null && newDirective.importPath == insertAfter.importPath) return insertAfter

            (importList.addAfter(newDirective, insertAfter) as KtImportDirective).also {
                importList.addBefore(psiFactory.createNewLine(1), it)
            }
        }
    } else {
        error("Trying to insert import $fqName into a file $name of type ${this::class.java} with no import list.")
    }
}
