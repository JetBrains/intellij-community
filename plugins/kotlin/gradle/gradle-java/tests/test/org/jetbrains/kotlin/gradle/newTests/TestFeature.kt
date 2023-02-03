// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import java.io.File

interface TestFeature<V : Any> {
    fun createDefaultConfiguration(): V

    /**
     * Renders human-readable description of test feature' configuration. It will be
     * rendered in the testdata
     *
     * General guidelines:
     * - return one or more strings. Each string will be rendered as a separate line, so it's
     *   a good idea go group related information together, and separate less related ones
     * - try to keep it short and informative, akin to commit message titles: 'hide stdlib', 'show order entries scopes'
     */
    fun renderConfiguration(configuration: V): List<String> = emptyList()

    // Happens after main setUp executed, order with other 'before'-methods coming
    // from JUnit rules is not defined (don't mix them)
    fun additionalSetUp() { }

    fun KotlinMppTestsContext.beforeTestExecution() { }

    fun KotlinMppTestsContext.beforeImport() { }

    fun preprocessFile(origin: File, text: String): String? = null

    fun KotlinMppTestsContext.afterImport() { }

    // Happens before setUp executed, order with other 'after'-methods coming
    // from JUnit rules is not defined (don't mix them)
    //
    // Lifecycle-wise it's not very different from afterImport, but it is guaranteed to be executed
    // if additionalSetUp has been executed, while afterImport doesn't have this guarantee
    fun additionalTearDown() {}
}
