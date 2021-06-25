// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.generator.methods

import org.jetbrains.kotlin.testGenerator.generator.*
import org.jetbrains.kotlin.testGenerator.model.TAnnotation

class SetUpMethod(private val codeLines: List<String>) : TestMethod {
    override val methodName = "setUp"

    override fun Code.render() {
        appendAnnotation(TAnnotation<Override>())
        appendBlock("protected void $methodName()") {
            codeLines.forEach { appendLine(it) }
            append("super.setUp();")
        }
    }
}