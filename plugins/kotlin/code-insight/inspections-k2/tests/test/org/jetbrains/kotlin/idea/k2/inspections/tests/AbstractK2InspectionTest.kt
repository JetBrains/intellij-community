// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import org.jetbrains.kotlin.idea.codeInsight.AbstractInspectionTest

abstract class AbstractK2InspectionTest : AbstractInspectionTest() {
    override fun isFirPlugin() = true
    override fun inspectionClassDirective() = "// K2_INSPECTION_CLASS:"
    override fun registerGradlPlugin() {}
}