// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.psi.setPropertyInitializer
import org.jetbrains.kotlin.idea.base.psi.singleExpressionBody
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Checks whether the accessor can be converted to an initializer.
 * This is only possible for getters and only if the property does not already have an initializer.
 * Additionally, there are circumstances where a property cannot have an initializer, such as
 * if the property is declared in an interface or as `expect`.
 */
@ApiStatus.Internal
fun KtPropertyAccessor.canConvertToInitializer(): Boolean {
    if (!isGetter || singleExpressionBody() == null) return false
    val property = property
    val containingClassOrObject = property.containingClassOrObject
    return !property.hasInitializer() &&
            annotationEntries.isEmpty() &&
            property.receiverTypeReference == null &&
            property.modifierList?.contextParameterList == null &&
            (containingClassOrObject as? KtClass)?.isInterface() != true &&
            property.modifierList?.hasModifier(KtTokens.EXPECT_KEYWORD) != true &&
            containingClassOrObject?.hasExpectModifier() != true
}

/**
 * If this accessor has a [singleExpressionBody], moves the expression to be
 * the property's initializer instead.
 */
@ApiStatus.Internal
fun KtPropertyAccessor.convertSingleExpressionGetterToInitializer(updater: ModPsiUpdater) {
    val property = property
    val singleExpression = singleExpressionBody() ?: return
    val commentSaver = CommentSaver(property)
    property.setPropertyInitializer(singleExpression)

    val setter = property.setter
    val anchor = if (setter != null && setter.startOffset < this.startOffset) setter else property.initializer
    property.deleteChildRange(anchor?.nextSibling, this)
    updater.moveCaretTo(property.endOffset)
    commentSaver.restore(property)
}
