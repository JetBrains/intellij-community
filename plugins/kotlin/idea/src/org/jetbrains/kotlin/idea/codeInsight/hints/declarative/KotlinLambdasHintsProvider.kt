// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType.LAMBDA_IMPLICIT_PARAMETER_RECEIVER
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType.LAMBDA_RETURN_EXPRESSION

@K1Deprecation
class KotlinLambdasHintsProvider : AbstractKotlinInlayHintsProvider(LAMBDA_RETURN_EXPRESSION, LAMBDA_IMPLICIT_PARAMETER_RECEIVER)