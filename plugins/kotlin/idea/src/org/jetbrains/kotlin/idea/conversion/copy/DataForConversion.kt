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

            val newFileText = clipTextIfNeeded(file, fileText, startOffsets, endOffsets)
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

        private fun clipTextIfNeeded(file: PsiJavaFile, fileText: String, startOffsets: IntArray, endOffsets: IntArray): String? {
            val ranges = startOffsets.indices.map { TextRange(startOffsets[it], endOffsets[it]) }.sortedBy { it.startOffset }

            fun canDropRange(range: TextRange) = ranges.all { range !in it }

            val rangesToDrop = ArrayList<TextRange>()
            for (range in ranges) {
                val start = range.startOffset
                val end = range.endOffset
                if (start == end) continue

                val startToken = file.findElementAt(start)!!
                val elementToClipLeft = startToken.maximalParentToClip(range)
                if (elementToClipLeft != null) {
                    val elementStart = elementToClipLeft.textRange.startOffset
                    if (elementStart < start) {
                        val clipBound = tryClipLeftSide(elementToClipLeft, start)
                        if (clipBound != null) {
                            val rangeToDrop = TextRange(elementStart, clipBound)
                            if (canDropRange(rangeToDrop)) {
                                rangesToDrop.add(rangeToDrop)
                            }
                        }
                    }
                }

                val endToken = file.findElementAt(end - 1)!!
                val elementToClipRight = endToken.maximalParentToClip(range)
                if (elementToClipRight != null) {
                    val elementEnd = elementToClipRight.textRange.endOffset
                    if (elementEnd > end) {
                        val clipBound = tryClipRightSide(elementToClipRight, end)
                        if (clipBound != null) {
                            val rangeToDrop = TextRange(clipBound, elementEnd)
                            if (canDropRange(rangeToDrop)) {
                                rangesToDrop.add(rangeToDrop)
                            }
                        }
                    }
                }
            }

            if (rangesToDrop.isEmpty()) return null

            val newFileText = buildString {
                var offset = 0
                for (range in rangesToDrop) {
                    assert(range.startOffset >= offset)
                    append(fileText.substring(offset, range.startOffset))
                    offset = range.endOffset
                }
                append(fileText.substring(offset, fileText.length))
            }

            fun IntArray.update() {
                for (range in rangesToDrop.asReversed()) {
                    for (i in indices) {
                        val offset = this[i]
                        if (offset >= range.endOffset) {
                            this[i] = offset - range.length
                        } else {
                            assert(offset <= range.startOffset)
                        }
                    }
                }
            }

            startOffsets.update()
            endOffsets.update()

            return newFileText
        }

        private fun PsiElement.maximalParentToClip(range: TextRange): PsiElement? {
            val firstNotInRange = parentsWithSelf
                .takeWhile { it !is PsiDirectory }
                .firstOrNull { it.textRange !in range }
                ?: return null
            return firstNotInRange.parentsWithSelf.lastOrNull { it.minimizedTextRange() in range }
        }

        private fun PsiElement.minimizedTextRange(): TextRange {
            val firstChild = firstChild?.siblings()?.firstOrNull { !canDropElementFromText(it) } ?: return textRange
            val lastChild = lastChild.siblings(forward = false).first { !canDropElementFromText(it) }
            return TextRange(firstChild.minimizedTextRange().startOffset, lastChild.minimizedTextRange().endOffset)
        }

        // element's text can be removed from file's text keeping parsing the same
        private fun canDropElementFromText(element: PsiElement): Boolean {
            return when (element) {
                is PsiWhiteSpace, is PsiComment, is PsiModifierList, is PsiAnnotation -> true
                is PsiJavaToken -> {
                    when (element.tokenType) {
                        // modifiers
                        JavaTokenType.PUBLIC_KEYWORD, JavaTokenType.PROTECTED_KEYWORD, JavaTokenType.PRIVATE_KEYWORD,
                        JavaTokenType.STATIC_KEYWORD, JavaTokenType.ABSTRACT_KEYWORD, JavaTokenType.FINAL_KEYWORD,
                        JavaTokenType.NATIVE_KEYWORD, JavaTokenType.SYNCHRONIZED_KEYWORD, JavaTokenType.STRICTFP_KEYWORD,
                        JavaTokenType.TRANSIENT_KEYWORD, JavaTokenType.VOLATILE_KEYWORD, JavaTokenType.DEFAULT_KEYWORD ->
                            element.getParent() is PsiModifierList
                        JavaTokenType.SEMICOLON -> true
                        else -> false
                    }
                }
                is PsiCodeBlock -> element.getParent() is PsiMethod
                else -> element.firstChild == null
            }
        }

        private fun tryClipLeftSide(element: PsiElement, leftBound: Int) =
            tryClipSide(element, leftBound, { textRange }, { allChildren })

        private fun tryClipRightSide(element: PsiElement, rightBound: Int): Int? {
            fun Int.transform() = Int.MAX_VALUE - this
            fun TextRange.transform() = TextRange(endOffset.transform(), startOffset.transform())
            return tryClipSide(
                element,
                rightBound.transform(),
                { textRange.transform() },
                { lastChild.siblings(forward = false) }
            )?.transform()
        }

        private fun tryClipSide(
            element: PsiElement,
            rangeBound: Int,
            rangeFunction: PsiElement.() -> TextRange,
            childrenFunction: PsiElement.() -> Sequence<PsiElement>
        ): Int? {
            if (element.firstChild == null) return null

            val elementRange = element.rangeFunction()
            assert(elementRange.startOffset < rangeBound && rangeBound < elementRange.endOffset)

            var clipTo = elementRange.startOffset
            for (child in element.childrenFunction()) {
                val childRange = child.rangeFunction()

                if (childRange.startOffset >= rangeBound) { // we have cut enough already
                    break
                } else if (childRange.endOffset <= rangeBound) { // need to drop the whole element
                    if (!canDropElementFromText(child)) return null
                    clipTo = childRange.endOffset
                } else { // rangeBound is inside child's range
                    if (child is PsiWhiteSpace) break // no need to cut whitespace - we can leave it as is
                    return tryClipSide(child, rangeBound, rangeFunction, childrenFunction)
                }
            }

            return clipTo
        }

        private fun ElementAndTextList.collectElementsToConvert(
            file: PsiJavaFile,
            fileText: String,
            range: TextRange
        ) {
            val elements = file.elementsInRange(range)
            if (elements.isEmpty()) {
                add(fileText.substring(range.startOffset, range.endOffset))
            } else {
                add(fileText.substring(range.startOffset, elements.first().textRange.startOffset))
                elements.forEach {
                    when {
                        it is PsiComment -> {
                            // don't convert comments separately, because they will become attached
                            // to the neighboring real elements and so may be duplicated
                            return@forEach
                        }
                        shouldExpandToChildren(it) -> this += it.allChildren.toList()
                        else -> this += it
                    }
                }
                add(fileText.substring(elements.last().textRange.endOffset, range.endOffset))
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
