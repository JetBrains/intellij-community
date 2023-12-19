// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import org.jetbrains.kotlin.idea.codeInsight.hints.HintType

class KotlinLambdasHintsProvider :
    AbstractKotlinInlayHintsProvider(HintType.LAMBDA_RETURN_EXPRESSION, HintType.LAMBDA_IMPLICIT_PARAMETER_RECEIVER) {

    companion object {
        const val PROVIDER_ID: String = "kotlin.lambdas.hints"

        const val SHOW_RETURN_EXPRESSIONS = "hints.lambda.return"
        const val SHOW_IMPLICIT_RECEIVERS_AND_PARAMS = "hints.lambda.receivers.parameters"
    }

}