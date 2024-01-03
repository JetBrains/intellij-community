// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.postfix.test

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.codeInsight.postfix.AbstractKotlinPostfixTemplateTestBase

abstract class AbstractK2PostfixTemplateTest : AbstractKotlinPostfixTemplateTestBase() {
    override val pluginKind: KotlinPluginMode
        get() = KotlinPluginMode.K2
}