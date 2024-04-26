// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.actions.ExpectedTypeWithNullability
import com.intellij.lang.jvm.types.JvmType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFromUsageUtil.toNullability

class ExpectedKotlinType(val ktType: KtType, jvmType: JvmType) : ExpectedTypeWithNullability(jvmType, ktType.nullability.toNullability())