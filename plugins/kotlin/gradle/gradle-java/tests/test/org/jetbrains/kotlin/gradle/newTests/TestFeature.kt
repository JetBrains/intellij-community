// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

interface TestFeature<V : Any> {
    /**
     * Renders human-readable description of test feature' configuration. It will be
     * rendered in the testdata
     *
     * General guidelines:
     * - return one or more strings. Each string will be rendered as a separate line, so it's
     *   a good idea go group related information together, and separate less related ones
     * - try to keep it short and informative, akin to commit message titles: 'hide stdlib', 'show order entries scopes'
     */
    fun renderConfiguration(configuration: V): List<String>

    fun createDefaultConfiguration(): V
}
