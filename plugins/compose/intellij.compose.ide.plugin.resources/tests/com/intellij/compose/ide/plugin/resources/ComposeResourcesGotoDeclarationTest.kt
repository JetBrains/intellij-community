// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.compose.ide.plugin.resources.psi.asUnderscoredIdentifier
import com.intellij.openapi.application.EDT
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.daemon.common.trimQuotes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@ComposeResourcesAllSourceSets
class ComposeResourcesGotoDeclarationTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test composeResources are accessible`() = testComposeResourcesProject {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      openInEditor()

      doTestNavigation(qualifiedName = "Res.drawable.test", expectedSize = 1, expectedType = ResourceType.DRAWABLE)
      doTestNavigation(qualifiedName = "Res.drawable.compose_multiplatform", expectedSize = 2, expectedType = ResourceType.DRAWABLE)

      doTestNavigation(qualifiedName = "Res.string.test", expectedSize = 4, expectedType = ResourceType.STRING)
      doTestNavigation(qualifiedName = "Res.array.test", expectedSize = 4, expectedType = ResourceType.STRING_ARRAY)
      doTestNavigation(qualifiedName = "Res.plurals.test", expectedSize = 4, expectedType = ResourceType.PLURAL_STRING)

      doTestNavigation(qualifiedName = "Res.font.test", expectedSize = 1, expectedType = ResourceType.FONT)
    }
  }

  private fun openInEditor() {
    codeInsightFixture.configureFromExistingVirtualFile(
      canonicalProjectFile("composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt")
    )
  }

  private fun doTestNavigation(qualifiedName: String, expectedSize: Int, expectedType: ResourceType) {
    codeInsightFixture.editor.caretModel.moveToOffset(codeInsightFixture.file.text.indexOf(qualifiedName) + qualifiedName.length)

    val targetElements = GotoDeclarationAction.findAllTargetElements(project, codeInsightFixture.editor, codeInsightFixture.caretOffset)

    assertEquals(expectedSize, targetElements.size, "$qualifiedName in ${codeInsightFixture.file.virtualFile.path}")
    targetElements.forEach {
      val actualName = if (expectedType.isStringType) it.text.trimQuotes() else it.namedUnwrappedElement?.name?.substringBefore('.')?.asUnderscoredIdentifier()
      assertEquals(qualifiedName.substringAfterLast('.'), actualName)

      val actualTypeName = if (expectedType.isStringType) it.parentOfType<XmlTag>()?.name else it.parent.namedUnwrappedElement?.name?.asUnderscoredIdentifier()
      assertTrue(actualTypeName?.startsWith(expectedType.typeName) == true)
    }
  }
}