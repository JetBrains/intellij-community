/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.TestOnly
import java.util.*

fun configureInspections(tools: Array<InspectionProfileEntry>,
                         project: Project,
                         parentDisposable: Disposable): InspectionProfileImpl {
  val profile = InspectionProfileImpl.createSimple(UUID.randomUUID().toString(), project, tools.map { InspectionToolRegistrar.wrapTool(it) })
//  profile.disableToolByDefault(disabledInspections, project)

  val profileManager = ProjectInspectionProfileManager.getInstanceImpl(project)

  val oldRootProfile = profileManager.currentProfile
  Disposer.register(parentDisposable, Disposable {
    profileManager.deleteProfile(profile)
    profileManager.setCurrentProfile(oldRootProfile)
    clearAllToolsIn(InspectionProfileImpl.getDefaultProfile(), project)
  })

  profileManager.setRootProfile(profile.name)
  InspectionProfileImpl.initAndDo<Any>({
                                         profileManager.addProfile(profile)
                                         profile.initInspectionTools(project)
                                         profileManager.setCurrentProfile(profile)
                                         null
                                       })
  return profile
}

@JvmOverloads
@TestOnly
fun createGlobalContextForTool(scope: AnalysisScope,
                               project: Project,
                               toolWrappers: List<InspectionToolWrapper<*, *>> = emptyList()): GlobalInspectionContextForTests {
  val profile = InspectionProfileImpl.createSimple("test", project, toolWrappers)
  val context = object : GlobalInspectionContextForTests(project, (InspectionManagerEx.getInstance(project) as InspectionManagerEx).contentManager) {
    override fun getUsedTools(): List<Tools> {
      return InspectionProfileImpl.initAndDo {
        for (tool in toolWrappers) {
          profile.enableTool(tool.shortName, project)
        }
        profile.getAllEnabledInspectionTools(project)
      }
    }
  }
  context.currentScope = scope
  return context
}

private fun clearAllToolsIn(profile: InspectionProfileImpl, project: Project) {
  for (state in profile.getAllTools(project)) {
    val wrapper = state.tool
    if (wrapper.extension != null) {
      // make it not initialized
      ReflectionUtil.resetField(wrapper, InspectionProfileEntry::class.java, "myTool")
    }
  }
}

fun ProjectInspectionProfileManager.createProfile(localInspectionTool: LocalInspectionTool, disposable: Disposable): InspectionProfileImpl {
  return configureInspections(arrayOf(localInspectionTool), project, disposable)
}