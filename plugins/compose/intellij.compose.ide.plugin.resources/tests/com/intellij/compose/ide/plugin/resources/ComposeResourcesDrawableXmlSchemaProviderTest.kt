// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import org.junit.runners.Parameterized.Parameters

class ComposeResourcesDrawableXmlSchemaProviderTest : ComposeResourcesTestCase() {

  companion object {
    @JvmStatic
    @Suppress("ACCIDENTAL_OVERRIDE")
    @Parameters(name = "{index}: source set {1} with Gradle-{0}")
    // XML schema handling is source-set agnostic, test data is in commonMain to avoid duplication
    fun data(): Collection<Any> = listOf(arrayOf(TARGET_GRADLE_VERSION, COMMON_MAIN))
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test vector drawable highlighting has no unresolved tag errors`() = checkHighlightingWarnings(
    xmlContent = """
      <vector xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:aapt="http://schemas.android.com/aapt"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24"
        android:viewportHeight="24">
        <path android:pathData="M0,0 L24,0 L24,24 L0,24 z">
          <aapt:attr name="android:fillColor">
            <gradient
              android:startX="0"
              android:startY="0"
              android:endX="24"
              android:endY="24"
              android:type="linear">
              <item android:color="#FF0000" android:offset="0"/>
              <item android:color="#0000FF" android:offset="1"/>
            </gradient>
          </aapt:attr>
        </path>
      </vector>
    """,
    resourceType = ResourceType.DRAWABLE
  )

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test vector drawable highlighting reports errors for unexpected namespace uri`() = checkHighlightingWarnings(
    xmlContent = """
      <vector xmlns:android="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">https://example.invalid/not-android</error>"
        xmlns:aapt="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">https://example.invalid/not-aapt</error>"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24"
        android:viewportHeight="24">
        <path android:pathData="M0,0 L24,0 L24,24 L0,24 z">
          <aapt:attr name="android:fillColor">
            <gradient
              android:startX="0"
              android:startY="0"
              android:endX="24"
              android:endY="24">
              <item android:color="#FF0000" android:offset="0"/>
              <item android:color="#0000FF" android:offset="1"/>
            </gradient>
          </aapt:attr>
        </path>
      </vector>
    """,
    resourceType = ResourceType.DRAWABLE
  )

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test vector drawable highlighting accepts tools namespace`() = checkHighlightingWarnings(
    xmlContent = """
      <vector xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24"
        android:viewportHeight="24"
        tools:targetApi="21">
        <path android:pathData="M0,0 L24,0 L24,24 L0,24 z"
          android:fillColor="#FF0000"/>
      </vector>
    """,
    resourceType = ResourceType.DRAWABLE
  )

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test vector drawable highlighting accepts auto namespace`() = checkHighlightingWarnings(
    xmlContent = """
      <vector xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24"
        android:viewportHeight="24"
        app:customAttr="value">
        <path android:pathData="M0,0 L24,0 L24,24 L0,24 z"
          android:fillColor="#FF0000"/>
      </vector>
    """,
    resourceType = ResourceType.DRAWABLE
  )

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test vector drawable highlighting reports error for res package namespace`() = checkHighlightingWarnings(
    xmlContent = """
      <vector xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://schemas.android.com/apk/res/com.example.app</error>"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24"
        android:viewportHeight="24"
        app:customAttr="value">
        <path android:pathData="M0,0 L24,0 L24,24 L0,24 z"
          android:fillColor="#FF0000"/>
      </vector>
    """,
    resourceType = ResourceType.DRAWABLE
  )

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test values resources highlighting accepts xliff and standard namespaces`() = checkHighlightingWarnings(
    xmlContent = """
      <resources
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
        <string name="welcome_message">
          Welcome back, <xliff:g id="username">user</xliff:g>!
        </string>
      </resources>
    """,
    resourceType = ResourceType.STRING
  )

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test values resources highlighting reports error for res package namespace`() = checkHighlightingWarnings(
    xmlContent = """
      <resources 
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:xliff="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">urn:oasis:names:tc:xliff:invalid</error>">
        <string name="welcome">Hello</string>
      </resources>
    """,
    resourceType = ResourceType.STRING
  )

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test string array resources highlighting accepts xliff namespace`() = checkHighlightingWarnings(
    xmlContent = """
      <resources
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
        <string-array name="steps">
          <item>First step</item>
          <item>Second step with <xliff:g id="detail">value</xliff:g></item>
        </string-array>
      </resources>
    """,
    resourceType = ResourceType.STRING_ARRAY
  )

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test plurals resources highlighting accepts xliff namespace`() = checkHighlightingWarnings(
    xmlContent = """
      <resources
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
        <plurals name="notification_count">
          <item quantity="one">You have <xliff:g id="count">N</xliff:g> new message.</item>
          <item quantity="other">You have <xliff:g id="count">N</xliff:g> new messages.</item>
        </plurals>
      </resources>
    """,
    resourceType = ResourceType.PLURAL_STRING
  )

  private fun checkHighlightingWarnings(xmlContent: String, resourceType: ResourceType) {
    val files = importProjectFromTestData()
    val expectedDirName = resourceType.dirName

    val targetFile = files.find {
      it.path.contains("/$sourceSetName/composeResources/$expectedDirName") && it.name.endsWith(".xml")
    } ?: error("Could not find any XML asset file inside '$sourceSetName/composeResources/$expectedDirName'")

    runWriteAction {
      codeInsightTestFixture.saveText(targetFile, xmlContent.trimIndent())
    }

    codeInsightTestFixture.configureFromExistingVirtualFile(targetFile)
    codeInsightTestFixture.checkHighlighting(true, false, false)
  }
}
