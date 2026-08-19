// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration
import org.jetbrains.idea.devkit.inspections.remotedev.BackendActionInFrontendGroupInspection
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import kotlin.time.Duration.Companion.seconds

internal class BackendActionInFrontendGroupInspectionTest : JavaCodeInsightFixtureTestCase() {
  companion object {
    private const val PROBLEM_DESCRIPTION =
      "Backend action 'BackendAction' cannot be registered in frontend action group 'FrontendGroup'"
  }

  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    RegistryManager.getInstance().get("devkit.split.mode.inspections.enable.in.implicit.module.kind")
      .setValue(true, testRootDisposable)

    val service = SplitModeApiRestrictionsService.getInstance(project)
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }

    myFixture.addClass(
      "package com.example;\n\nimport org.jetbrains.annotations.NotNull;\n\npublic class TestAction extends com.intellij.openapi.actionSystem.AnAction " +
      "{ @Override\npublic void actionPerformed(com.intellij.openapi.actionSystem.@NotNull AnActionEvent e) {} }"
    )
    myFixture.enableInspections(BackendActionInFrontendGroupInspection())
  }

  fun testBackendActionAddedToFrontendGroup() {
    addDescriptor("frontend.module", "frontend", """
      <idea-plugin>
        <dependencies><module name="intellij.platform.frontend"/></dependencies>
        <actions><group id="FrontendGroup"/></actions>
      </idea-plugin>
    """.trimIndent())
    val backend = addDescriptor("backend.module", "backend", """
      <idea-plugin>
        <dependencies><module name="intellij.platform.backend"/></dependencies>
        <actions>
          <action id="BackendAction" class="com.example.TestAction">
            <add-to-group
              group-id="${problem("FrontendGroup")}"/>
          </action>
        </actions>
      </idea-plugin>
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(backend.virtualFile)
    myFixture.checkHighlighting()
  }

  fun testBackendActionAddedToBackendGroup() {
    val backend = addDescriptor("backend.module", "backend", """
      <idea-plugin>
        <dependencies><module name="intellij.platform.backend"/></dependencies>
        <actions>
          <group id="BackendGroup"/>
          <action id="BackendAction" class="com.example.TestAction">
            <add-to-group group-id="BackendGroup"/>
          </action>
        </actions>
      </idea-plugin>
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(backend.virtualFile)
    myFixture.checkHighlighting()
  }

  fun testFrontendActionAddedToFrontendGroup() {
    val frontend = addDescriptor("frontend.module", "frontend", """
      <idea-plugin>
        <dependencies><module name="intellij.platform.frontend"/></dependencies>
        <actions>
          <group id="FrontendGroup"/>
          <action id="FrontendAction" class="com.example.TestAction">
            <add-to-group group-id="FrontendGroup"/>
          </action>
        </actions>
      </idea-plugin>
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(frontend.virtualFile)
    myFixture.checkHighlighting()
  }

  fun testBackendActionReferenceNestedInFrontendGroup() {
    addDescriptor("backend.module", "backend", """
      <idea-plugin>
        <dependencies><module name="intellij.platform.backend"/></dependencies>
        <actions><action id="BackendAction" class="com.example.TestAction"/></actions>
      </idea-plugin>
    """.trimIndent())
    val frontend = addDescriptor("frontend.module", "frontend", """
      <idea-plugin>
        <dependencies><module name="intellij.platform.frontend"/></dependencies>
        <actions>
          <group id="FrontendGroup">
            <reference
              ref="${problem("BackendAction")}"/>
          </group>
        </actions>
      </idea-plugin>
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(frontend.virtualFile)
    myFixture.checkHighlighting()
  }

  fun testBackendActionReferenceAddedToFrontendGroup() {
    addDescriptor("backend.module", "backend", """
      <idea-plugin>
        <dependencies><module name="intellij.platform.backend"/></dependencies>
        <actions><action id="BackendAction" class="com.example.TestAction"/></actions>
      </idea-plugin>
    """.trimIndent())
    addDescriptor("frontend.module", "frontend", """
      <idea-plugin>
        <dependencies><module name="intellij.platform.frontend"/></dependencies>
        <actions><group id="FrontendGroup"/></actions>
      </idea-plugin>
    """.trimIndent())
    val placement = addDescriptor("placement.module", "placement", """
      <idea-plugin>
        <actions>
          <reference ref="BackendAction">
            <add-to-group
              group-id="${problem("FrontendGroup")}"/>
          </reference>
        </actions>
      </idea-plugin>
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(placement.virtualFile)
    myFixture.checkHighlighting()
  }

  private fun addDescriptor(moduleName: String, directoryName: String, content: String): PsiFile {
    val existingModule = ModuleManager.getInstance(project).findModuleByName(moduleName)
    val module = existingModule ?: PsiTestUtil.addModule(
      project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName),
    ).also {
      PsiTestUtil.addSourceRoot(
        it,
        myFixture.tempDirFixture.findOrCreateDir("$moduleName/resources"),
        JavaResourceRootType.RESOURCE,
      )
    }
    val descriptor = myFixture.addFileToProject("$moduleName/resources/META-INF/$directoryName.xml", content)
    assertNotNull(descriptor)
    val pluginBuildConfiguration = requireNotNull(PluginBuildConfiguration.getInstance(module)) {
      "Plugin build configuration is unavailable for module '$moduleName'"
    }
    pluginBuildConfiguration.setPluginXmlFromVirtualFile(descriptor.virtualFile)
    return descriptor
  }

  private fun problem(value: String): String = "<error descr=\"$PROBLEM_DESCRIPTION\">$value</error>"
}
