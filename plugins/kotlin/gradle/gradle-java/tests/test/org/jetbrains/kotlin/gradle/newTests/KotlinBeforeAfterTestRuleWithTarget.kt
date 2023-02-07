// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement


interface KotlinBeforeAfterTestRuleWithTarget : MethodRule {
    override fun apply(base: Statement, method: FrameworkMethod, target: Any): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    before(target)
                    base.evaluate()
                }
                finally {
                    after(target)
                }
            }
        }
    }

    fun before(target: Any) { }

    /**
     * [after] is guaranteed to be called even if the test or [before] finished with an exception
     */
    fun after(target: Any) { }
}
