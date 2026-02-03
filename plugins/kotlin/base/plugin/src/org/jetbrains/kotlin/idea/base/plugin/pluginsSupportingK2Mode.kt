// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.isPluginWhichDependsOnKotlinPluginInK2ModeAndItDoesNotSupportK2Mode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun getPluginsDependingOnKotlinPluginInK2ModeAndIncompatibleWithIt(): List<IdeaPluginDescriptorImpl> {
    return PluginManagerCore.getPluginSet().allPlugins
        .filter { plugin ->
            isPluginWhichDependsOnKotlinPluginInK2ModeAndItDoesNotSupportK2Mode(plugin)
        }
}