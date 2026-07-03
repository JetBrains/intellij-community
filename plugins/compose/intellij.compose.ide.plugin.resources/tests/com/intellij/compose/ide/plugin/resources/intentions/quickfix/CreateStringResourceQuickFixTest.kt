// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.compose.ide.plugin.resources.ComposeResourcesAllSourceSets
import com.intellij.compose.ide.plugin.resources.ComposeResourcesCodeInsightTestCase
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.STRINGS_XML_FILENAME
import com.intellij.compose.ide.plugin.resources.getComposeResourcesDir
import com.intellij.compose.ide.plugin.resources.intentions.hasSubTagWithName
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.assertNotNull as kAssertNotNull

@ComposeResourcesAllSourceSets
internal class CreateStringResourceQuickFixTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test quickfix adds string entry`() = doQuickFix(codeLine = "Res.string.new_string_resource")

  @Test
  fun `test quickfix adds string-array entry`() = doQuickFix(codeLine = "Res.array.new_array_resource")

  @Test
  fun `test quickfix adds plurals entry`() = doQuickFix(codeLine = "Res.plurals.new_plurals_resource")

  @Test
  fun `test quickfix does not add duplicate string entry`() = doQuickFix(expectAdded = false, codeLine = "Res.string.test")

  @Test
  fun `test quickfix does not add duplicate string-array entry`() = doQuickFix(expectAdded = false, codeLine = "Res.array.test")

  @Test
  fun `test quickfix does not add duplicate plurals entry`() = doQuickFix(expectAdded = false, codeLine = "Res.plurals.test")

  @Test
  fun `test quickfix does nothing on drawable`() = doQuickFix(expectAdded = false, expectAvailable = false, "Res.drawable.test")

  private fun doQuickFix(
    expectAdded: Boolean = true,
    expectAvailable: Boolean = true,
    codeLine: String,
  ) = testComposeResourcesProject {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      try {
        val sourceKtFilePath = "composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt"
        val sourceKtFile = getFile(sourceKtFilePath)
        val stringsXmlFile = findStringsXmlFile(project, sourceSetName)
        snapshotProjectFile(projectRelativePath(stringsXmlFile))
        val (resourceType, resourceName) = parseResReference(codeLine)

        val quickFix = CreateStringResourceQuickFix(
          resourceName = resourceName,
          resourceType = resourceType,
          sourceKtFileUrl = sourceKtFile.url,
          stringsXmlUrl = stringsXmlFile.url,
          composeResourcesDirUrl = stringsXmlFile.parent.parent.url,
        )

        if (!expectAvailable) {
          assertFalse(quickFix.isAvailable(project, null, null), "QuickFix should not be available")
          return@timeoutRunBlocking
        }

        invokeAndAssertQuickFixResult(
          quickFix = quickFix,
          composeResourcesDirVirtualFile = stringsXmlFile.parent.parent,
          project = project,
          codeLine = codeLine,
          expectAdded = expectAdded,
        )
      }
      finally {
        revertUnsavedDocuments()
      }
    }
  }
}

internal fun List<VirtualFile>.findTestFiles(project: Project, sourceSetName: String): Pair<VirtualFile, VirtualFile> {
  val sourceKtFile = first {
    it.path.endsWith("composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt")
  }

  return sourceKtFile to findStringsXmlFile(project, sourceSetName)
}

private fun findStringsXmlFile(project: Project, sourceSetName: String): VirtualFile {
  val module = ModuleManager.getInstance(project).modules.first {
    it.name.endsWith(".$sourceSetName") || (sourceSetName == "androidMain" && it.name.endsWith(".main"))
  }
  val composeResourcesDir = module.getComposeResourcesDir()!!
  val valuesDir = composeResourcesDir.findChild(ResourceType.STRING.dirName)!!
  val stringsXmlFile = valuesDir.findChild(STRINGS_XML_FILENAME)!!

  return stringsXmlFile
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
    ?.findChild(STRINGS_XML_FILENAME)
    ?.let { psiManager.findFile(it) as? XmlFile }

  val resourceExistedBefore = stringsXmlFileBefore
                                ?.rootTag
                                ?.hasSubTagWithName(resourceType.typeName, resourceName)
                              ?: false

  assertTrue(quickFix.isAvailable(project, null, null), "QuickFix should be available")
  quickFix.invoke(project, null, null)
  PsiDocumentManager.getInstance(project).commitAllDocuments()

  val stringsXmlFileVirtualFile = kAssertNotNull(
    composeResourcesDirVirtualFile
      .findChild(ResourceType.STRING.dirName)
      ?.findChild(STRINGS_XML_FILENAME),
    "Strings.xml file should not be null"
  )

  val stringsXmlFile = kAssertNotNull(psiManager.findFile(stringsXmlFileVirtualFile) as? XmlFile, "Strings.xml file should not be null")

  val rootTagAfter = kAssertNotNull(stringsXmlFile.rootTag, "Root tag should not be null")
  val resourceExistsNow = rootTagAfter.hasSubTagWithName(resourceType.typeName, resourceName)

  if (expectAdded) {
    assertFalse(resourceExistedBefore, "Resource '$resourceName' should not exist before the quickfix")
    assertTrue(resourceExistsNow, "Expected <${resourceType.typeName}> tag with name='$resourceName' to be added")
  }
  else {
    assertTrue(resourceExistedBefore, "Resource '$resourceName' should already exist before the quickfix")
    val count = rootTagAfter.findSubTags(resourceType.typeName).count { it.getAttributeValue("name") == resourceName }
    assertEquals(1, count, "Expected exactly one <${resourceType.typeName}> tag with name='$resourceName'")
  }
}

private fun parseResReference(codeLine: String): Pair<ResourceType, String> {
  val (accessor, resourceName) = codeLine.substringAfter("Res").trimStart().removePrefix(".").trim()
    .split(".")
    .map { it.trim() }
  val resourceType = ResourceType.fromAccessor(accessor)
  return resourceType to resourceName
}
