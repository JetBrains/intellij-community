// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.editor

import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.options.ex.ConfigurableWrapper

class KotlinEditorOptionsTopHitProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
    override fun getId(): String = ID

    override fun getOptions(): Collection<OptionDescription> =
        kotlinEditorOptionsDescriptors
            .map { c -> if (c is ConfigurableWrapper) c.configurable else c }
            .filterIsInstance<OptionDescription>().toList()
}
