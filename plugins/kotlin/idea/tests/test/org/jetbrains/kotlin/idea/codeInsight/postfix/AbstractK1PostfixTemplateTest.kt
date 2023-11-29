// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind

abstract class AbstractK1PostfixTemplateTest : AbstractKotlinPostfixTemplateTestBase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FE10_PLUGIN
}