// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION_ERROR", "unused")
package org.jetbrains.kotlin.idea.codeinsight.hints

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType as NewRangeKtExpressionType
import org.jetbrains.kotlin.idea.codeinsight.utils.getRangeBinaryExpressionType as newGetRangeBinaryExpressionType
import org.jetbrains.kotlin.idea.codeinsight.utils.getRangeLeftAndRightSigns as newGetRangeLeftAndRightSigns
import org.jetbrains.kotlin.idea.codeinsight.utils.illegalLiteralPrefixOrSuffix as newIllegalLiteralPrefixOrSuffix
import org.jetbrains.kotlin.idea.codeinsight.utils.isFollowedByNewLine as newIsFollowedByNewLine

@Deprecated("Moved to org.jetbrains.kotlin.idea.codeinsight.utils", ReplaceWith("isFollowedByNewLine()", "org.jetbrains.kotlin.idea.codeinsight.utils.isFollowedByNewLine"), level = DeprecationLevel.HIDDEN)
fun ASTNode.isFollowedByNewLine(): Boolean = newIsFollowedByNewLine()

@Deprecated("Moved to org.jetbrains.kotlin.idea.codeinsight.utils", ReplaceWith("RangeKtExpressionType", "org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType"), level = DeprecationLevel.HIDDEN)
typealias RangeKtExpressionType = NewRangeKtExpressionType

@Deprecated("Moved to org.jetbrains.kotlin.idea.codeinsight.utils", ReplaceWith("getRangeBinaryExpressionType(expression)", "org.jetbrains.kotlin.idea.codeinsight.utils.getRangeBinaryExpressionType"), level = DeprecationLevel.HIDDEN)
fun getRangeBinaryExpressionType(expression: KtExpression): NewRangeKtExpressionType? = newGetRangeBinaryExpressionType(expression)

@Deprecated("Moved to org.jetbrains.kotlin.idea.codeinsight.utils", ReplaceWith("getRangeLeftAndRightSigns()", "org.jetbrains.kotlin.idea.codeinsight.utils.getRangeLeftAndRightSigns"), level = DeprecationLevel.HIDDEN)
fun KtExpression.getRangeLeftAndRightSigns(): Pair<String, String?>? = newGetRangeLeftAndRightSigns()

@Deprecated("Moved to org.jetbrains.kotlin.idea.codeinsight.utils", ReplaceWith("illegalLiteralPrefixOrSuffix()", "org.jetbrains.kotlin.idea.codeinsight.utils.illegalLiteralPrefixOrSuffix"), level = DeprecationLevel.HIDDEN)
fun PsiElement.illegalLiteralPrefixOrSuffix(): Boolean = newIllegalLiteralPrefixOrSuffix()