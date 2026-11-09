// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionWrapperUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.runInInitMode
import java.util.UUID

/**
 * A `Plugin.xml registration check` inspection that declares [moduleSets] as its plugin module sets.
 */
fun registrationCheckWithModuleSets(vararg moduleSets: List<String>): LocalInspectionTool {
  val registrationCheck = PluginXmlRegistrationCheckInspection()
  moduleSets.forEach { moduleNames ->
    registrationCheck.pluginsModules.add(PluginXmlRegistrationCheckInspection.PluginModuleSet().apply {
      modules = LinkedHashSet(moduleNames)
    })
  }
  return registrationCheck
}

/**
 * Adds a project profile that is not the current one, like a team profile next to the profile a user switched to.
 * Its registration check declares [moduleSets]. A light project is shared between tests, so the profile is removed
 * on [disposable].
 */
fun addProjectProfileWithPluginModuleSets(project: Project, disposable: Disposable, vararg moduleSets: List<String>) {
  val toolSupplier = InspectionToolsSupplier.Simple(listOf(InspectionWrapperUtil.wrapTool(registrationCheckWithModuleSets(*moduleSets))))
  Disposer.register(disposable, toolSupplier)
  val profile = InspectionProfileImpl(UUID.randomUUID().toString(), toolSupplier, null)
  runInInitMode { profile.initInspectionTools(project) }
  val profileManager = ProjectInspectionProfileManager.getInstance(project)
  profileManager.addProfile(profile)
  Disposer.register(disposable) { profileManager.deleteProfile(profile) }
}
