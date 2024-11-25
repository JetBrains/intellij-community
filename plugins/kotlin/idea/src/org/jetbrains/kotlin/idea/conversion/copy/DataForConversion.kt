// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings


data class DataForConversion private constructor(
    val elementsAndTexts: ElementAndTextList /* list consisting of PsiElement's to convert and plain String's */,
    val importsAndPackage: String,
    val file: PsiJavaFile
) {
    companion object {
        fun prepare(copiedCode: CopiedJavaCode, project: Project): DataForConversion {
            val startOffsets = copiedCode.startOffsets.clone()
            val endOffsets = copiedCode.endOffsets.clone()
            assert(startOffsets.size == endOffsets.size) { "Must have the same size" }

            var fileText = copiedCode.fileText
            var file = PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, fileText) as PsiJavaFile

            val importsAndPackage = buildImportsAndPackage(file)

            val newFileText = ConversionTextClipper.clipTextIfNeeded(file, fileText, startOffsets, endOffsets)
            if (newFileText != null) {
                fileText = newFileText
                file = PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, newFileText) as PsiJavaFile
            }

            val elementsAndTexts = ElementAndTextList()
            for (i in startOffsets.indices) {
                elementsAndTexts.collectElementsToConvert(file, fileText, TextRange(startOffsets[i], endOffsets[i]))
            }

            return DataForConversion(elementsAndTexts, importsAndPackage, file)
        }

        private fun ElementAndTextList.collectElementsToConvert(
            file: PsiJavaFile,
            fileText: String,
            range: TextRange
        ) {
            val elements = file.elementsInRange(range)
            if (elements.isEmpty()) {
                addText(fileText.substring(range.startOffset, range.endOffset))
            } else {
                addText(fileText.substring(range.startOffset, elements.first().textRange.startOffset))
                elements.forEach {
                    when {
                        it is PsiComment -> {
                            // don't convert comments separately, because they will become attached
                            // to the neighboring real elements and so may be duplicated
                            return@forEach
                        }
                        shouldExpandToChildren(it) -> addElements(it.allChildren.toList())
                        else -> addElement(it)
                    }
                }
                addText(fileText.substring(elements.last().textRange.endOffset, range.endOffset))
            }
        }

        // converting of PsiModifierList is not supported by converter, but converting of single annotations inside it is supported
        private fun shouldExpandToChildren(element: PsiElement) = element is PsiModifierList

        private fun buildImportsAndPackage(sourceFile: PsiJavaFile): String {
            return buildString {
                val packageName = sourceFile.packageName
                if (packageName.isNotEmpty()) {
                    append("package $packageName\n")
                }

                val importList = sourceFile.importList
                if (importList != null) {
                    for (import in importList.importStatements) {
                        val qualifiedName = import.qualifiedName ?: continue
                        if (import.isOnDemand) {
                            append("import $qualifiedName.*\n")
                        } else {
                            val fqName = FqNameUnsafe(qualifiedName)
                            // skip explicit imports of platform classes mapped into Kotlin classes
                            if (fqName.isSafe && JavaToKotlinClassMap.isJavaPlatformClass(fqName.toSafe())) continue
                            append("import $qualifiedName\n")
                        }
                    }
                    //TODO: static imports
                }
            }
        }
    }
}
