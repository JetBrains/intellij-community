// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class GenerateEqualsFix(function: String) : GenerateFunctionFix(function) {
    override fun getName(): String = KotlinBundle.message("equals.text")
}
