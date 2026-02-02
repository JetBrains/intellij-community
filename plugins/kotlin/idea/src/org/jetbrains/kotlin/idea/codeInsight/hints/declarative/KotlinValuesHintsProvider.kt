// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType

@K1Deprecation
class KotlinValuesHintsProvider : AbstractKotlinInlayHintsProvider(HintType.RANGES)
