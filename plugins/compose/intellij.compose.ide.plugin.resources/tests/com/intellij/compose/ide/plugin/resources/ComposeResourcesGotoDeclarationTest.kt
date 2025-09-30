// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.compose.ide.plugin.resources.psi.asUnderscoredIdentifier
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.daemon.common.trimQuotes
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertNotNull as kAssertNotNull

class ComposeResourcesGotoDeclarationTest : ComposeResourcesTestCase() {
  private var _codeInsightTestFixture: CodeInsightTestFixture? = null

  private val codeInsightTestFixture: CodeInsightTestFixture
    get() = kAssertNotNull(_codeInsightTestFixture, "_codeInsightTestFixture was not initialized")

  override fun setUpFixtures() {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
    _codeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
    codeInsightTestFixture.setUp()
    codeInsightTestFixture.testDataPath = PathManagerEx.getCommunityHomePath() + "/plugins/compose/intellij.compose.ide.plugin.resources/testData/"
  }

  override fun tearDownFixtures() {
    runAll(
      { _codeInsightTestFixture?.tearDown() },
      { _codeInsightTestFixture = null },
      { myTestFixture = null },
    )
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test common composeResources are accessible`() = runBlocking(Dispatchers.EDT) {
    assumeTrue("temporarily disable for androidMain since it's not recognised as source root", sourceSetName != ANDROID_MAIN)
    val files = importProjectFromTestData()
    files.openInEditor(sourceSetName = sourceSetName)

    doTestNavigation(qualifiedName = "Res.drawable.test", expectedSize = 1, expectedType = ResourceType.DRAWABLE)
    doTestNavigation(qualifiedName = "Res.drawable.compose_multiplatform", expectedSize = 2, expectedType = ResourceType.DRAWABLE)

    doTestNavigation(qualifiedName = "Res.string.test", expectedSize = 4, expectedType = ResourceType.STRING)
    doTestNavigation(qualifiedName = "Res.array.test", expectedSize = 1, expectedType = ResourceType.STRING_ARRAY)
    doTestNavigation(qualifiedName = "Res.plurals.test", expectedSize = 1, expectedType = ResourceType.PLURAL_STRING)

    doTestNavigation(qualifiedName = "Res.font.test", expectedSize = 1, expectedType = ResourceType.FONT)
  }

  private fun List<VirtualFile>.openInEditor(sourceSetName: String) =
    codeInsightTestFixture.openFileInEditor(first { it.path.endsWith("composeApp/src/$sourceSetName/kotlin/org/example/project/test.$sourceSetName.kt") })

  private fun doTestNavigation(qualifiedName: String, expectedSize: Int, expectedType: ResourceType) {
    codeInsightTestFixture.editor.caretModel.moveToOffset(codeInsightTestFixture.file.text.indexOf(qualifiedName) + qualifiedName.length)

    val targetElements = GotoDeclarationAction.findAllTargetElements(project, codeInsightTestFixture.editor, codeInsightTestFixture.caretOffset)

    assertSize(expectedSize, targetElements)
    targetElements.forEach {
      val actualName = if (expectedType.isStringType) it.text.trimQuotes() else it.namedUnwrappedElement?.name?.substringBefore('.')?.asUnderscoredIdentifier()
      assertEquals(qualifiedName.substringAfterLast('.'), actualName)

      val actualTypeName = if (expectedType.isStringType) it.parentOfType<XmlTag>()?.name else it.parent.namedUnwrappedElement?.name?.asUnderscoredIdentifier()
      assertTrue(actualTypeName?.startsWith(expectedType.typeName) == true)
    }
  }
}