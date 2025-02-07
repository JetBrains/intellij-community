// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.Nullability
import com.intellij.lang.jvm.actions.ExpectedTypeWithNullability
import com.intellij.lang.jvm.types.JvmType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.toNullability

class ExpectedKotlinType private constructor(
    val kaType: KaType,
    jvmType: JvmType,
    nullability: Nullability,
) : ExpectedTypeWithNullability(jvmType, nullability) {
    
    companion object {
        context(KaSession)
        fun create(kaType: KaType, jvmType: JvmType): ExpectedKotlinType = 
            ExpectedKotlinType(kaType, jvmType, kaType.nullability.toNullability())
    }
    
}