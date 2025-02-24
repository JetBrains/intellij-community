// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.settings

import KotlinGradleScriptingBundle
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel

class KotlinStandaloneScriptsModel private constructor(scripts: MutableList<String>) :
    ListTableModel<String>(
        arrayOf(object : ColumnInfo<String, String>(KotlinGradleScriptingBundle.message("standalone.scripts.settings.column.name")) {
            override fun valueOf(item: String?): String? = item
        }),
        scripts
    ) {

    override fun setItems(items: List<String>) {
        super.setItems(items.toMutableList())
    }

    companion object {
        fun createModel(scripts: Collection<String>): KotlinStandaloneScriptsModel =
            KotlinStandaloneScriptsModel(scripts.toMutableList())
    }
}
