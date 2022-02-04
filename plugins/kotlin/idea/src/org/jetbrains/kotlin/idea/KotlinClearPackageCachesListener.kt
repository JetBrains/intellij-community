// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade

class KotlinClearPackageCachesListener(private val project: Project) : DynamicPluginListener, ModuleRootListener {
    override fun rootsChanged(event: ModuleRootEvent): Unit = clearPackageCaches()

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean): Unit = clearPackageCaches()
    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean): Unit = clearPackageCaches()
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor): Unit = clearPackageCaches()

    private fun clearPackageCaches(): Unit = KotlinJavaPsiFacade.getInstance(project).clearPackageCaches()
}


