// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test.matcher

abstract class MatcherModel() {
    abstract fun report(message: String)
    fun checkReport(subject: String, expected: Any?, actual: Any?) {
        if (expected != actual) {
            report(
                """
                    |$subject differs:
                    |expected $expected
                    |actual:  $actual
                """.trimMargin()
            )
        }
    }
}