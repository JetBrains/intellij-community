// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.module.PluginModuleType
import javax.swing.Icon

@TestDataPath($$"$CONTENT_ROOT/testData/navigation/descriptorsIncludingContentModule")
class DescriptorsIncludingContentModuleLineMarkerProviderTest : JavaCodeInsightFixtureTestCase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/descriptorsIncludingContentModule"

  fun testSingleContentModuleTarget() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
          <module name="test.module.extra"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea<caret>-plugin>
        <!-- any -->
      </idea-plugin>
      """.trimIndent()
    )

    testGutterTargets(
      tooltip = "Shared content module test.module is included in 1 plugin XML descriptors",
      expectedIcon = AllIcons.Nodes.Module,
      expectedTargets = listOf(
        "declaring.module.xml | <module name=\"test.module\"/>"
      )
    )
  }

  fun testSingleContentModuleTargetWhenMultipleContentExistInAFile() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module.extra"/>
        </content>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea<caret>-plugin>
        <!-- any -->
      </idea-plugin>
      """.trimIndent()
    )

    testGutterTargets(
      tooltip = "Shared content module test.module is included in 1 plugin XML descriptors",
      expectedIcon = AllIcons.Nodes.Module,
      expectedTargets = listOf(
        "declaring.module.xml | <module name=\"test.module\"/>"
      )
    )
  }

  fun testMultipleContentModuleTargets() {
    createModuleWithModuleDescriptor("declaring.module.1", "declaring.module.1.xml", """
      <idea-plugin>
        <content>
          <module name="test.module.extra"/>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
    createModuleWithModuleDescriptor("declaring.module.2", "declaring.module.2.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
          <module name="test.module.extra"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea<caret>-plugin>
        <!-- any -->
      </idea-plugin>
      """.trimIndent()
    )

    testGutterTargets(
      tooltip = "Shared content module test.module is included in 2 plugin XML descriptors",
      expectedIcon = AllIcons.Nodes.Module,
      expectedTargets = listOf(
        "declaring.module.1.xml | <module name=\"test.module\"/>",
        "declaring.module.2.xml | <module name=\"test.module\"/>"
      )
    )
  }

  fun testContentModuleTargetsWithDifferentLoadingOptions() {
    createModuleWithModuleDescriptor("declaring.module.1", "declaring.module.1.xml", """
      <idea-plugin>
        <content>
          <module name="test.module.extra"/>
          <module name="test.module" loading="embedded"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
    createModuleWithModuleDescriptor("declaring.module.2", "declaring.module.2.xml", """
      <idea-plugin>
        <content>
          <module name="test.module" loading="on-demand"/>
          <module name="test.module.extra"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea<caret>-plugin>
        <!-- any -->
      </idea-plugin>
      """.trimIndent()
    )

    testGutterTargets(
      tooltip = "Shared content module test.module is included in 2 plugin XML descriptors",
      expectedIcon = AllIcons.Nodes.Module,
      expectedTargets = listOf(
        "declaring.module.1.xml | <module name=\"test.module\" loading=\"embedded\"/>",
        "declaring.module.2.xml | <module name=\"test.module\" loading=\"on-demand\"/>"
      )
    )
  }

  fun testSharedNamedContentModuleUsesSharedIcon() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module.shared"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module.shared", "test.module.shared.xml", """
      <idea<caret>-plugin>
        <!-- any -->
      </idea-plugin>
      """.trimIndent()
    )

    testGutterTargets(
      tooltip = "Shared content module test.module.shared is included in 1 plugin XML descriptors",
      expectedIcon = DevkitCoreIcons.SharedModule,
      expectedTargets = listOf(
        "declaring.module.xml | <module name=\"test.module.shared\"/>"
      ),
      descriptorPath = "test.module.shared/test.module.shared.xml",
    )
  }

  fun testFrontendContentModuleTooltipAndIcon() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea<caret>-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )

    testGutterTargets(
      tooltip = "Frontend content module test.module is included in 1 plugin XML descriptors",
      expectedIcon = DevkitCoreIcons.FrontendModule,
      expectedTargets = listOf(
        "declaring.module.xml | <module name=\"test.module\"/>"
      )
    )
  }

  fun testNotGutterWhenNoContentModuleReferences() {
    createModuleWithModuleDescriptor("declaring.module.1", "declaring.module.1.xml", """
      <idea-plugin>
        <dependencies> <!-- not 'content' shouldn't be a target -->
          <module name="test.module.extra"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    createModuleWithModuleDescriptor("declaring.module.2", "declaring.module.2.xml", """
      <idea-plugin>
        <depends>test.module</depends> <!-- not 'content' shouldn't be a target -->
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea<caret>-plugin>
        <!-- any -->
      </idea-plugin>
      """.trimIndent()
    )

    assertNull(myFixture.findGutter("test.module/test.module.xml"))
  }

  private fun createModuleWithModuleDescriptor(
    moduleName: String,
    moduleDescriptorName: String,
    @Language("XML") moduleDescriptorText: String,
  ) {
    PsiTestUtil.addModule(
      project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
    myFixture.addFileToProject("$moduleName/$moduleDescriptorName", moduleDescriptorText)
  }

  private fun testGutterTargets(
    tooltip: String,
    expectedIcon: Icon,
    expectedTargets: List<String>,
    descriptorPath: String = "test.module/test.module.xml",
  ) {
    val gutter = myFixture.findGutter(descriptorPath)
    DevKitGutterTargetsChecker.checkGutterTargets(
      gutter,
      tooltip,
      expectedIcon,
      { renderTargetElement(it) },
      *expectedTargets.toTypedArray()
    )
  }

  private fun renderTargetElement(element: PsiElement): String {
    val file = element.containingFile
    val tagText = element.parentOfType<XmlTag>(true) ?: throw IllegalStateException("Cannot find parent XmlTag")
    return "${file.name} | ${tagText.text}"
  }
}
