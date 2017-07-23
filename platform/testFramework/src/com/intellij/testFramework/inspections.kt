/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.mapSmart
import gnu.trove.THashMap
import org.jetbrains.annotations.TestOnly
import java.util.*

fun configureInspections(tools: Array<InspectionProfileEntry>,
                         project: Project,
                         parentDisposable: Disposable): InspectionProfileImpl {
  runInInitMode {
    val profile = createSimple(UUID.randomUUID().toString(), project, tools.mapSmart { InspectionToolRegistrar.wrapTool(it) })
    val profileManager = ProjectInspectionProfileManager.getInstance(project)
    // we don't restore old project profile because in tests it must be in any case null - app default profile
    Disposer.register(parentDisposable, Disposable {
      profileManager.deleteProfile(profile)
      profileManager.setCurrentProfile(null)
      clearAllToolsIn(BASE_PROFILE)
    })

    profileManager.addProfile(profile)
    profile.initInspectionTools(project)
    profileManager.setCurrentProfile(profile)
    return profile
  }
}

@JvmOverloads
@TestOnly
fun createGlobalContextForTool(scope: AnalysisScope,
                               project: Project,
                               toolWrappers: List<InspectionToolWrapper<*, *>> = emptyList()): GlobalInspectionContextForTests {
  runInInitMode {
    val profile = createSimple("test", project, toolWrappers)
    val context = object : GlobalInspectionContextForTests(project, (InspectionManagerEx.getInstance(project) as InspectionManagerEx).contentManager) {
      override fun getUsedTools(): List<Tools> {
        for (tool in toolWrappers) {
          profile.enableTool(tool.shortName, project)
        }
        return profile.getAllEnabledInspectionTools(project)
      }
    }
    context.currentScope = scope
    return context
  }
}

private fun clearAllToolsIn(profile: InspectionProfileImpl) {
  if (!profile.wasInitialized()) {
    return
  }

  for (state in profile.allTools) {
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

fun enableInspectionTool(project: Project, tool: InspectionProfileEntry, disposable: Disposable) = enableInspectionTool(project, InspectionToolRegistrar.wrapTool(tool), disposable)

fun enableInspectionTools(project: Project, disposable: Disposable, vararg tools: InspectionProfileEntry) {
  for (tool in tools) {
    enableInspectionTool(project, InspectionToolRegistrar.wrapTool(tool), disposable)
  }
}

fun enableInspectionTool(project: Project, toolWrapper: InspectionToolWrapper<*, *>, disposable: Disposable) {
  val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
  val shortName = toolWrapper.shortName
  val key = HighlightDisplayKey.find(shortName)
  if (key == null) {
    HighlightDisplayKey.register(shortName, toolWrapper.displayName, toolWrapper.id)
  }

  runInInitMode {
    val existingWrapper = profile.getInspectionTool(shortName, project)
    if (existingWrapper == null || existingWrapper.isInitialized != toolWrapper.isInitialized || toolWrapper.isInitialized && toolWrapper.tool !== existingWrapper.tool) {
      profile.addTool(project, toolWrapper, THashMap<String, List<String>>())
    }
    profile.enableTool(shortName, project)
  }
  Disposer.register(disposable, Disposable { profile.setToolEnabled(shortName, false) })
}

inline fun <T> runInInitMode(runnable: () -> T): T {
  val old = InspectionProfileImpl.INIT_INSPECTIONS
  try {
    InspectionProfileImpl.INIT_INSPECTIONS = true
    return runnable()
  }
  finally {
    InspectionProfileImpl.INIT_INSPECTIONS = old
  }
}

fun disableInspections(project: Project, vararg inspections: InspectionProfileEntry) {
  val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
  for (inspection in inspections) {
    profile.setToolEnabled(inspection.shortName, false)
  }
}

fun InspectionProfileImpl.disableAllTools() {
  for (entry in getInspectionTools(null)) {
    setToolEnabled(entry.shortName, false)
  }
}