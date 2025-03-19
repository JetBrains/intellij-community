// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.compilerPreferences.facet

import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import org.jetbrains.kotlin.utils.DescriptionAware
import javax.swing.ListCellRenderer

internal fun <T> createDescriptionAwareRenderer(): ListCellRenderer<T?> {
    return textListCellRenderer("") {
        (it as? DescriptionAware)?.description
    }
}
