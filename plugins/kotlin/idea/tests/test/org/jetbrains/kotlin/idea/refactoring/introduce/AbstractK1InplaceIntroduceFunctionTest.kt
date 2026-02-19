// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler

abstract class AbstractK1InplaceIntroduceFunctionTest: AbstractInplaceIntroduceFunctionTest() {
    override fun getExtractFunctionHandler(allContainersEnabled: Boolean): AbstractExtractKotlinFunctionHandler = ExtractKotlinFunctionHandler(allContainersEnabled)
}

