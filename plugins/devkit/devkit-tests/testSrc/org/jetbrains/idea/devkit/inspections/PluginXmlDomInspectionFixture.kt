// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * The fixture of the [PluginXmlDomInspection] tests. Create it with [pluginXmlDomInspectionFixture].
 *
 * The project has the stub of `ApplicationComponent`. The fixture enables [PluginXmlDomInspection] and [XmlUnresolvedReferenceInspection].
 */
class PluginXmlDomInspectionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Adds the stubs of `DataContext`, `AnAction` and `ActionGroup`.
   * With [actionGroupHasCanBePerformedMethod], `ActionGroup` declares `canBePerformed`.
   */
  fun setUpActionClasses(actionGroupHasCanBePerformedMethod: Boolean) {
    fixture.addClass("package com.intellij.openapi.actionSystem; public interface DataContext {}")
    fixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}")

    val actionGroupBody = if (!actionGroupHasCanBePerformedMethod) ""
    else "public boolean canBePerformed(@NotNull DataContext context) {\n  return false;\n}\n"
    fixture.addClass("package com.intellij.openapi.actionSystem;\n\n" +
                     "public abstract class ActionGroup extends AnAction {\n $actionGroupBody\n}\n")
  }
}

/**
 * Creates a [PluginXmlDomInspectionFixture] for one test. Declare it as an instance field.
 *
 * [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun pluginXmlDomInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<PluginXmlDomInspectionFixture> = testFixture("pluginXmlDomInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  fixture.addClass("package com.intellij.openapi.components; public interface ApplicationComponent {}")
  fixture.enableInspections(PluginXmlDomInspection(), XmlUnresolvedReferenceInspection())
  initialized(PluginXmlDomInspectionFixture(fixture)) {}
}
