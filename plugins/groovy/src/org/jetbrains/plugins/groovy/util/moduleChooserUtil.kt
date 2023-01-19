// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleChooserUtil")

package org.jetbrains.plugins.groovy.util

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.Consumer
import com.intellij.util.Function
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil

private const val GROOVY_LAST_MODULE = "Groovy.Last.Module.Chosen"

fun selectModule(project: Project,
                 modules: List<Module>,
                 version: Function<Module, @Nls String>,
                 consumer: Consumer<Module>) {
  modules.singleOrNull()?.let {
    consumer.consume(it)
    return
  }
  createSelectModulePopup(project, modules, version::`fun`, consumer::consume).showCenteredInCurrentWindow(project)
}

fun createSelectModulePopup(project: Project,
                            modules: List<Module>,
                            version: (Module) -> @Nls String,
                            consumer: (Module) -> Unit): ListPopup {
  val step = createSelectModulePopupStep(project, modules.sortedWith(ModulesAlphaComparator.INSTANCE), consumer)
  return object : ListPopupImpl(project, step) {
    override fun getListElementRenderer() = RightTextCellRenderer(super.getListElementRenderer(), version)
  }
}

private fun createSelectModulePopupStep(project: Project, modules: List<Module>, consumer: (Module) -> Unit): ListPopupStep<Module> {
  val propertiesComponent = PropertiesComponent.getInstance(project)

  val step = GroovySelectModuleStep(modules) {
    propertiesComponent.setValue(GROOVY_LAST_MODULE, it.name)
    consumer(it)
  }

  propertiesComponent.getValue(GROOVY_LAST_MODULE)?.let { lastModuleName ->
    step.defaultOptionIndex = modules.indexOfFirst { it.name == lastModuleName }
  }

  return step
}

fun hasJavaSdk(module: Module): Boolean = ModuleRootManager.getInstance(module).sdk?.sdkType is JavaSdkType

fun hasAcceptableModuleType(module: Module): Boolean = GroovyFacetUtil.isAcceptableModuleType(ModuleType.get(module))
