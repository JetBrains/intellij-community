// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import org.jetbrains.kotlin.idea.base.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.KotlinPluginKindProvider

class FE10KotlinPluginKindProvider: KotlinPluginKindProvider() {
    override fun getPluginKind(): KotlinPluginKind = KotlinPluginKind.FE10_PLUGIN
}