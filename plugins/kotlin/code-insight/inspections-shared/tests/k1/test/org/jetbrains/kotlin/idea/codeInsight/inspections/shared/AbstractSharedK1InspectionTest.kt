// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.inspections.AbstractInspectionTest
import org.jetbrains.kotlin.test.util.invalidateK1ModeCaches

abstract class AbstractSharedK1InspectionTest: AbstractInspectionTest() {
    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateK1ModeCaches() } },
            { super.tearDown() },
        )
    }
}