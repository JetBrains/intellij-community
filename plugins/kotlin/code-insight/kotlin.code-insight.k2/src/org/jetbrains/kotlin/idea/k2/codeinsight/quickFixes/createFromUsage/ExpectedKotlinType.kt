// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.types.JvmType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope

internal class ExpectedKotlinType(val type: JvmType) : ExpectedType {
    override fun getTheType(): JvmType = type

    override fun getTheKind(): ExpectedType.Kind = ExpectedType.Kind.EXACT

    companion object {
        fun createExpectedKotlinType(type: JvmType): ExpectedKotlinType = ExpectedKotlinType(type)

        /**
         * A placeholder to denote "This type is invalid". Only thing this type does is returning `false` for `isValid()` function.
         */
        val INVALID_TYPE = createExpectedKotlinType(object : PsiType(emptyArray()) {
            override fun <A : Any?> accept(visitor: PsiTypeVisitor<A>): A {
                return visitor.visitType(PsiTypes.nullType())
            }

            override fun getPresentableText(): String = ""

            override fun getCanonicalText(): String = ""

            override fun isValid(): Boolean = false

            override fun equalsToText(text: String): Boolean = false

            override fun getResolveScope(): GlobalSearchScope? = null

            override fun getSuperTypes(): Array<PsiType> = emptyArray()
        })
    }
}