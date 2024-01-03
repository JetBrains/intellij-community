// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.plugin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKindProvider

class K2KotlinPluginKindProvider : KotlinPluginKindProvider {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.K2
}