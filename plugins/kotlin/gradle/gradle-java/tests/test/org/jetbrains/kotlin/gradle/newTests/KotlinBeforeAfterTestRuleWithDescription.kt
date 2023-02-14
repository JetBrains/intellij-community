// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Similar to [org.junit.rules.ExternalResource], but additionally passes test description to
 * [before] and [after] methods.
 */
interface KotlinBeforeAfterTestRuleWithDescription : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        statement(base, description)

    private fun statement(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                before(description)
                try {
                    base.evaluate()
                }
                finally {
                    after(description)
                }
            }
        }


    fun before(description: Description): Unit = Unit
    fun after(description: Description): Unit = Unit
}
