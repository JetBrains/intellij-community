// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.dom.AddToGroup
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterActionFix
import org.jetbrains.idea.devkit.util.ActionData

/**
 * The fixture of the [ComponentNotRegisteredInspection] tests. Create it with [componentNotRegisteredFixture].
 *
 * The fixture enables [ComponentNotRegisteredInspection]. The project has the stubs of `AnAction` and `BaseComponent`.
 * Each `doTest` method is the body of one test. [fileExtension] is the extension of the source files.
 */
class ComponentNotRegisteredFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  PluginModuleFixture(fixture, fileExtension) {
  fun doTestRegisteredAction() {
    setPluginXml("registeredAction-plugin.xml")
    fixture.testHighlighting("RegisteredAction.$fileExtension")
  }

  fun doTestRegisteredActionInIDEAProject() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(fixture.project, true)
    try {
      fixture.copyFileToProject("registeredAction-plugin.xml", "someOtherPluginXmlName.xml")
      fixture.testHighlighting("RegisteredAction.$fileExtension")
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(fixture.project, false)
    }
  }

  fun doTestRegisteredActionInOptionalPluginDescriptor() {
    setPluginXml("registeredActionInOptionalPluginDescriptor-plugin.xml")
    fixture.copyFileToProject("registeredActionInOptionalPluginDescriptor-optional-plugin.xml", "META-INF/optional-plugin.xml")
    fixture.testHighlighting("RegisteredAction.$fileExtension")
  }

  fun doTestRegisteredInIncludedFileAction() {
    setPluginXml("ActionXInclude.xml")
    fixture.copyFileToProject("ActionXInclude_included.xml", "META-INF/ActionXInclude_included.xml")
    fixture.testHighlighting("ActionXInclude.$fileExtension")
  }

  /**
   * Call it from any thread. It applies the quick fix on the EDT.
   */
  fun doTestUnregisteredAction() {
    setPluginXml("unregisteredAction-plugin.xml")
    fixture.testHighlighting("UnregisteredAction.$fileExtension")

    RegisterActionFix.ourTestActionData = TestActionData("UnregisteredAction")
    try {
      runInEdtAndWait {
        val registerAction = fixture.findSingleIntention("Register Action")
        fixture.launchAction(registerAction)
        fixture.checkResultByFile("META-INF/plugin.xml", "unregisteredAction-plugin_after.xml", true)
      }
    }
    finally {
      RegisterActionFix.ourTestActionData = null
    }
  }

  fun doTestUnregisteredActionDeprecated() {
    fixture.testHighlighting("UnregisteredActionDeprecated.$fileExtension")
  }

  fun doTestUnregisteredActionUsedViaConstructor() {
    fixture.testHighlighting("UnregisteredActionUsedViaConstructor.$fileExtension")
  }

  fun doTestRegisteredApplicationComponent() {
    setPluginXml("registeredApplicationComponent-plugin.xml")
    fixture.testHighlighting("RegisteredApplicationComponent.$fileExtension")
  }

  fun doTestUnregisteredAbstractApplicationComponent() {
    fixture.testHighlighting("UnregisteredAbstractApplicationComponent.$fileExtension")
  }

  fun doTestUnregisteredApplicationComponentWithoutPluginXml() {
    fixture.testHighlighting("UnregisteredApplicationComponent.$fileExtension", "UnregisteredApplicationComponentInterface.$fileExtension")
  }

  fun doTestUnregisteredApplicationComponentWithRegisterFix() {
    setPluginXml("unregisteredApplicationComponent-plugin.xml")
    fixture.testHighlighting("UnregisteredApplicationComponent.$fileExtension", "UnregisteredApplicationComponentInterface.$fileExtension")
  }

  @Suppress("HardCodedStringLiteral")
  private class TestActionData(private val actionClassFqn: String) : ActionData {
    override fun getActionId(): String = StringUtil.getShortName(actionClassFqn)

    override fun getActionText(): String = "Action Text $actionClassFqn"

    override fun getActionDescription(): String = "Description $actionClassFqn"

    override fun getSelectedGroupId(): String = getActionId() + "Group"

    override fun getSelectedActionId(): String = getActionId() + "SelectedAction"

    override fun getSelectedAnchor(): String = AddToGroup.Anchor.before.name

    override fun getFirstKeyStroke(): String = "1st Key ${getActionId()}"

    override fun getSecondKeyStroke(): String = "2nd Key ${getActionId()}"
  }
}

/**
 * Creates a [ComponentNotRegisteredFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun componentNotRegisteredFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<ComponentNotRegisteredFixture> = testFixture("componentNotRegisteredFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath, fileExtension).init()
  val fixture = pluginModule.fixture
  fixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}")
  fixture.addClass("package com.intellij.openapi.components; public interface BaseComponent {}")
  fixture.enableInspections(ComponentNotRegisteredInspection())
  initialized(ComponentNotRegisteredFixture(fixture, fileExtension)) {}
}
