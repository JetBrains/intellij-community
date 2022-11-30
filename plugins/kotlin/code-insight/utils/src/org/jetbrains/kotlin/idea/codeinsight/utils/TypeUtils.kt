/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtType

context(KtAnalysisSession)
fun KtType.isNullableAnyType() = isAny && isMarkedNullable

context(KtAnalysisSession)
fun KtType.isNonNullableBooleanType() = isBoolean && !isMarkedNullable