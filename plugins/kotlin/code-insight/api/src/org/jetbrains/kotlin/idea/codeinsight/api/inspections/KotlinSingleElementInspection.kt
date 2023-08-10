// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitorVoid
import kotlin.reflect.KClass

/**
 * A [LocalInspectionTool] that visits elements of a single [elementType].
 */
abstract class KotlinSingleElementInspection<ELEMENT : KtElement>(
    val elementType: KClass<ELEMENT>,
) : LocalInspectionTool() {
    protected abstract fun visitTargetElement(element: ELEMENT, holder: ProblemsHolder, isOnTheFly: Boolean)

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)

                if (!elementType.isInstance(element) || element.textLength == 0) return
                @Suppress("UNCHECKED_CAST")
                visitTargetElement(element as ELEMENT, holder, isOnTheFly)
            }
        }
}
