// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.util.PropertiesCollection


class NewScriptFileInfo(
    var id: String = "",
    var title: String = "",
    var templateName: String = "Kotlin Script",
    var icon: Icon = KotlinIcons.SCRIPT
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NewScriptFileInfo

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

val IdeScriptCompilationConfigurationKeys.kotlinScriptTemplateInfo: PropertiesCollection.Key<NewScriptFileInfo> by PropertiesCollection.key()