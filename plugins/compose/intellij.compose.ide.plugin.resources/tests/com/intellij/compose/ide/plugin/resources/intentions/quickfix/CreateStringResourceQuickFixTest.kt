// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.compose.ide.plugin.resources.ComposeResourcesTestCase
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.STRINGS_XML_FILENAME
import com.intellij.compose.ide.plugin.resources.TARGET_GRADLE_VERSION
import com.intellij.compose.ide.plugin.resources.getComposeResourcesDir
import com.intellij.compose.ide.plugin.resources.intentions.hasSubTagWithName
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class CreateStringResourceQuickFixTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix adds string entry`() = doQuickFix(codeLine = "Res.string.new_string_resource")

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix adds string-array entry`() = doQuickFix(codeLine = "Res.array.new_array_resource")

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix adds plurals entry`() = doQuickFix(codeLine = "Res.plurals.new_plurals_resource")

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix does not add duplicate string entry`() = doQuickFix(expectAdded = false, codeLine = "Res.string.test")

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix does not add duplicate string-array entry`() = doQuickFix(expectAdded = false, codeLine = "Res.array.test")

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix does not add duplicate plurals entry`() = doQuickFix(expectAdded = false, codeLine = "Res.plurals.test")

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test quickfix does nothing on drawable`() = doQuickFix(expectAdded = false, expectAvailable = false, "Res.drawable.test")

  private fun doQuickFix(
    expectAdded: Boolean = true,
    expectAvailable: Boolean = true,
    codeLine: String,
  ) {
    invokeAndWaitIfNeeded(ModalityState.nonModal()) {
      val files = importProjectFromTestData()

      val (sourceKtFile, stringsXmlFile) = files.findTestFiles(myProject, sourceSetName)
      val (resourceType, resourceName) = parseResReference(codeLine)

      val quickFix = CreateStringResourceQuickFix(
        resourceName = resourceName,
        resourceType = resourceType,
        sourceKtFileUrl = sourceKtFile.url,
        stringsXmlUrl = stringsXmlFile.url,
        composeResourcesDirUrl = stringsXmlFile.parent.parent.url,
      )

      if (!expectAvailable) {
        assertFalse(quickFix.isAvailable(myProject, null, null), "QuickFix should not be available")
        return@invokeAndWaitIfNeeded
      }

      invokeAndAssertQuickFixResult(
        quickFix = quickFix,
        composeResourcesDirVirtualFile = stringsXmlFile.parent.parent,
        project = myProject,
        codeLine = codeLine,
        expectAdded = expectAdded,
      )
    }
  }
}

internal fun List<VirtualFile>.findTestFiles(project: Project, sourceSetName: String): Pair<VirtualFile, VirtualFile> {
  val sourceKtFile = first {
    it.path.endsWith("composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt")
  }

  val module = ModuleManager.getInstance(project).modules.first {
    it.name.endsWith(".$sourceSetName") || (sourceSetName == "androidMain" && it.name.endsWith(".main"))
  }
  val composeResourcesDir = module.getComposeResourcesDir()!!
  val valuesDir = composeResourcesDir.findChild(ResourceType.STRING.dirName)!!
  val stringsXmlFile = valuesDir.findChild(STRINGS_XML_FILENAME)!!

  return sourceKtFile to stringsXmlFile
}
internal fun invokeAndAssertQuickFixResult(
  quickFix: IntentionAction,
  composeResourcesDirVirtualFile: VirtualFile,
  project: Project,
  codeLine: String,
  expectAdded: Boolean = true,
) {
  val psiManager = PsiManager.getInstance(project)
  val (resourceType, resourceName) = parseResReference(codeLine)

  val stringsXmlFileBefore = composeResourcesDirVirtualFile
    .findChild(ResourceType.STRING.dirName)
    ?.let { psiManager.findFile(it) as? XmlFile }

  val resourceExistedBefore = stringsXmlFileBefore
                                ?.rootTag
                                ?.hasSubTagWithName(resourceType.typeName, resourceName)
                              ?: false

  assertTrue("QuickFix should be available") { quickFix.isAvailable(project, null, null) }
  quickFix.invoke(project, null, null)

  val stringsXmlFileVirtualFile = composeResourcesDirVirtualFile
    .findChild(ResourceType.STRING.dirName)
    ?.findChild(STRINGS_XML_FILENAME)
  assertNotNull(stringsXmlFileVirtualFile, "Strings.xml file should not be null")

  val stringsXmlFile = psiManager.findFile(stringsXmlFileVirtualFile) as? XmlFile
  assertNotNull(stringsXmlFile, "Strings.xml file should not be null")

  val rootTagAfter = stringsXmlFile.rootTag
  assertNotNull(rootTagAfter, "Root tag should not be null")
  val resourceExistsNow = rootTagAfter.hasSubTagWithName(resourceType.typeName, resourceName)

  if (expectAdded) {
    assertFalse(resourceExistedBefore, "Resource '$resourceName' should not exist before the quickfix")
    assertTrue(resourceExistsNow, "Expected <${resourceType.typeName}> tag with name='$resourceName' to be added")
  }
  else {
    assertTrue(resourceExistedBefore, "Resource '$resourceName' should already exist before the quickfix")
    val count = rootTagAfter.findSubTags(resourceType.typeName).count { it.getAttributeValue("name") == resourceName }
    assertEquals("Expected exactly one <${resourceType.typeName}> tag with name='$resourceName'", 1, count)
  }
}

private fun parseResReference(codeLine: String): Pair<ResourceType, String> {
  val (accessor, resourceName) = codeLine.substringAfter("Res").trimStart().removePrefix(".").trim()
    .split(".")
    .map { it.trim() }
  val resourceType = ResourceType.fromAccessor(accessor)
  return resourceType to resourceName
}
