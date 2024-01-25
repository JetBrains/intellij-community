// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType.*
import org.jetbrains.kotlin.idea.codeInsight.hints.NamedInlayInfoOption

val SHOW_RETURN_EXPRESSIONS = NamedInlayInfoOption("hints.lambda.return")
val SHOW_IMPLICIT_RECEIVERS_AND_PARAMS = NamedInlayInfoOption("hints.lambda.receivers.parameters")

class KotlinLambdasHintsProvider : AbstractKotlinInlayHintsProvider(LAMBDA_RETURN_EXPRESSION, LAMBDA_IMPLICIT_PARAMETER_RECEIVER)