// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.navigation.ItemWithPresentation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.codeVision.CodeVisionTestUtil
import com.intellij.ui.UiInterceptors
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JList

@TestApplication
internal class DescriptorsIncludingContentModuleCodeVisionProviderTest {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    CodeVisionTestUtil.setUp(myFixture)
  }

  @Test
  fun testSingleIncludingDescriptor() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
          <module name="test.module.extra"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    testCodeVision("""
      <!-- copyright -->
      <idea-plugin>/*<# [Included in 1 descriptor] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module is included in 1 plugin XML descriptor",
    )
  }

  @Test
  fun testSingleIncludingPlugin() {
    createModuleWithModuleDescriptor("declaring.module", "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.myplugin</id>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    testCodeVision("""
      <idea-plugin>/*<# [Included in 'com.example.myplugin' plugin] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module is included in 1 plugin XML descriptor",
    )
  }

  @Test
  fun testSingleIncludingPluginWithoutId() {
    createModuleWithModuleDescriptor("declaring.module", "META-INF/plugin.xml", """
      <idea-plugin>
        <name>My Example Plugin</name>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    testCodeVision("""
      <idea-plugin>/*<# [Included in 'My Example Plugin' plugin] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module is included in 1 plugin XML descriptor",
    )
  }

  @Test
  fun testMultipleIncludingPlugins() {
    createModuleWithModuleDescriptor("declaring.module.1", "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.myplugin.one</id>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
    createModuleWithModuleDescriptor("declaring.module.2", "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.myplugin.two</id>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    testCodeVision("""
      <idea-plugin>/*<# [Included in 2 descriptors] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module is included in 2 plugin XML descriptors",
    )
  }

  @Test
  fun testSingleIncludingDescriptorWhenMultipleContentExistInAFile() {
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

    testCodeVision("""
      <idea-plugin>/*<# [Included in 1 descriptor] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module is included in 1 plugin XML descriptor",
    )
  }

  @Test
  fun testMultipleIncludingDescriptors() {
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
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    testCodeVision("""
      <idea-plugin>/*<# [Included in 2 descriptors] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module is included in 2 plugin XML descriptors",
    )
  }

  @Test
  fun testSharedNamedContentModule() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module.shared"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    testCodeVision("""
      <idea-plugin>/*<# [Included in 1 descriptor] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module.shared is included in 1 plugin XML descriptor",
      moduleName = "test.module.shared",
    )
  }

  @Test
  fun testFrontendContentModule() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    testCodeVision("""
      <idea-plugin>/*<# [Included in 1 descriptor] #>*/
        <dependencies>
          <module name="intellij.platform.frontend"/>
          <module name="intellij.platform.rpc"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module is included in 1 plugin XML descriptor",
    )
  }

  @Test
  fun testLiteFrontendContentModule() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    testCodeVision("""
      <idea-plugin>/*<# [Included in 1 descriptor] #>*/
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = "Content module test.module is included in 1 plugin XML descriptor",
    )
  }

  @Test
  fun testNoCodeVisionWhenNoContentModuleReferences() {
    createModuleWithModuleDescriptor("declaring.module.1", "declaring.module.1.xml", """
      <idea-plugin>
        <dependencies> <!-- not 'content' shouldn't be a target -->
          <module name="test.module"/>
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

    testCodeVision("""
      <idea-plugin>
        <!-- any -->
      </idea-plugin>
      """.trimIndent()
    )
  }

  @Test
  fun testClickWithSingleIncludingDescriptorGoesToTheEntry() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module.extra"/>
          <module name="test.module" loading="required"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
    configureContentModuleDescriptor("<idea-plugin/>")

    val fileEditorManager = FileEditorManager.getInstance(myFixture.project)
    timeoutRunBlocking {
      clickCodeVisionEntry()
      waitUntil("The click did not open the including descriptor") {
        fileEditorManager.selectedEditor?.file?.name == "declaring.module.xml"
      }
    }
    val editor = fileEditorManager.selectedTextEditor!!
    val target = runReadActionBlocking {
      val file = PsiDocumentManager.getInstance(myFixture.project).getPsiFile(editor.document)!!
      val tag = file.findElementAt(editor.caretModel.offset)!!.parentOfType<XmlTag>(true)!!
      "${file.name} | ${tag.text}"
    }
    assertEquals("declaring.module.xml | <module name=\"test.module\" loading=\"required\"/>", target)
  }

  @Test
  fun testClickWithMultipleIncludingDescriptorsShowsThePopup() {
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
    createModuleWithModuleDescriptor("declaring.module.3", "declaring.module.3.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
    configureContentModuleDescriptor("<idea-plugin/>")

    var popupTargets = emptyList<String>()
    UiInterceptors.register(object : UiInterceptors.UiInterceptor<JBPopup>(JBPopup::class.java) {
      override fun doIntercept(component: JBPopup) {
        val list = UIUtil.findComponentOfType(component.content, JList::class.java)!!
        popupTargets = (0 until list.model.size).map { renderPopupItem(list.model.getElementAt(it) as ItemWithPresentation) }
        Disposer.dispose(component)
      }
    })

    timeoutRunBlocking {
      clickCodeVisionEntry()
    }
    UiInterceptors.clear()

    assertEquals(
      listOf(
        "declaring.module.1.xml | embedded | declaring.module.1 | <module name=\"test.module\" loading=\"embedded\"/>",
        "declaring.module.2.xml | on-demand | declaring.module.2 | <module name=\"test.module\" loading=\"on-demand\"/>",
        "declaring.module.3.xml | optional | declaring.module.3 | <module name=\"test.module\"/>",
      ),
      popupTargets.sorted(),
    )
  }

  @Test
  fun testPreview() {
    val previewPath = "codeVisionProviders/${DescriptorsIncludingContentModuleCodeVisionProvider.ID}/preview.xml"
    val previewText = DescriptorsIncludingContentModuleCodeVisionProvider::class.java.classLoader.getResource(previewPath)!!.readText()
    myFixture.configureByText("preview.xml", previewText)

    val provider = DescriptorsIncludingContentModuleCodeVisionProvider()
    provider.preparePreview(myFixture.editor, myFixture.file)
    val (range, entry) = runReadActionBlocking { provider.computeForEditor(myFixture.editor, myFixture.file) }.single()

    assertEquals(0, range.startOffset)
    assertEquals("Included in 2 descriptors", (entry as TextCodeVisionEntry).text)
  }

  private fun testCodeVision(
    @Language("XML") expectedText: String,
    expectedTooltip: String? = null,
    moduleName: String = "test.module",
  ) {
    CodeVisionTestUtil.testProviders(
      myFixture,
      expectedText,
      { sourceText -> configureContentModuleDescriptor(sourceText, moduleName) },
      false,
      DescriptorsIncludingContentModuleCodeVisionProvider.ID,
    )
    if (expectedTooltip != null) {
      assertEquals(expectedTooltip, runReadActionBlocking { computeCodeVisionEntry() }.tooltip)
    }
  }

  private fun configureContentModuleDescriptor(@Language("XML") descriptorText: String, moduleName: String = "test.module") {
    createModuleWithModuleDescriptor(moduleName, "$moduleName.xml", descriptorText)
    myFixture.configureFromTempProjectFile("$moduleName/$moduleName.xml")
  }

  private suspend fun clickCodeVisionEntry() {
    val entry = readAction { computeCodeVisionEntry() } as ClickableTextCodeVisionEntry
    withContext(Dispatchers.EDT) {
      entry.onClick(myFixture.editor)
    }
  }

  private fun computeCodeVisionEntry(): CodeVisionEntry {
    return DescriptorsIncludingContentModuleCodeVisionProvider().computeForEditor(myFixture.editor, myFixture.file).single().second
  }

  private fun renderPopupItem(item: ItemWithPresentation): String {
    val tag = item.dereference() as XmlTag
    val presentation = item.presentation
    return "${presentation.presentableText} | ${presentation.containerText} | ${presentation.locationText} | ${tag.text}"
  }

  private fun createModuleWithModuleDescriptor(
    moduleName: String,
    moduleDescriptorName: String,
    @Language("XML") moduleDescriptorText: String,
  ) {
    PsiTestUtil.addModule(
      myFixture.project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
    myFixture.addFileToProject("$moduleName/$moduleDescriptorName", moduleDescriptorText)
  }
}
