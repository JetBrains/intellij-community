// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared

import org.jetbrains.kotlin.idea.k2.inspections.tests.AbstractK2LocalInspectionTest

abstract class AbstractSharedK2LocalInspectionTest : AbstractK2LocalInspectionTest() {
    override val inspectionFileName: String = ".inspection"
}