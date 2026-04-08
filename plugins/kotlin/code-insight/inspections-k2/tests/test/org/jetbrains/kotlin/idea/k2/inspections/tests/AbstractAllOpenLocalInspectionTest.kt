// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.inspections.tests

import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.idea.k2.hints.compilerPlugins.withCompilerPlugin
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

/**
 * Local inspections test version with enabled `all-open` compiler plugin.
 *
 * The test is only generated for K2, so it doesn't use K2-specific directives because of only one test data version.
 * The predefined annotation class `@Open` that is passed to the all-open plugin options is added automatically and is not customizable.
 */
abstract class AbstractAllOpenLocalInspectionTest : AbstractLocalInspectionTest() {
    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun doTest(path: String) {
        myFixture.configureByText("Open.kt", "annotation class Open")
        module.withCompilerPlugin(
            KotlinK2BundledCompilerPlugins.ALL_OPEN_COMPILER_PLUGIN,
            "plugin:org.jetbrains.kotlin.allopen:annotation=Open"
        ) {
            super.doTest(path)
        }
    }
}
