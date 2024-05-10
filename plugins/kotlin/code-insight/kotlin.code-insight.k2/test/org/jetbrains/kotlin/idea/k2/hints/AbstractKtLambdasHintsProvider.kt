// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.AbstractKotlinLambdasHintsProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.KtLambdasHintsProvider

abstract class AbstractKtLambdasHintsProvider: AbstractKotlinLambdasHintsProvider() {

    override fun inlayHintsProvider(): InlayHintsProvider =
        KtLambdasHintsProvider()

}