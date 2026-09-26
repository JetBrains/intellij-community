package org.jetbrains.kotlin.idea.gradle.statistics

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ComposeLibraryUsagesCollectorTest {
    private lateinit var collector: ComposeLibraryUsagesCollector

    @BeforeMethod
    fun setUp() {
        collector = ComposeLibraryUsagesCollector()
    }

    @Test
    fun testSimpleLibraryName() {
        val definition = collector.extractLibraryDefinition("androidx.compose.ui:ui-android:1.0.0")
        assertNotNull(definition)
        assertEquals(definition.version, "1.0.0")
        assertEquals(definition.groupId, "androidx.compose.ui")
        assertEquals(definition.artifactId, "ui-android")
        assertEquals(definition.toLibraryCoordinates(), "androidx.compose.ui:ui-android")
    }

    @Test
    fun testDifferentLibraryName() {
        val definition = collector.extractLibraryDefinition("org.jetbrains.compose.ui:ui-desktop:2.3.4")
        assertNotNull(definition)
        assertEquals(definition.version, "2.3.4")
        assertEquals(definition.groupId, "org.jetbrains.compose.ui")
        assertEquals(definition.artifactId, "ui-desktop")
        assertEquals(definition.toLibraryCoordinates(), "org.jetbrains.compose.ui:ui-desktop")
    }

    @Test
    fun testWithPrefix() {
        val definition = collector.extractLibraryDefinition("Gradle: androidx.compose.ui:ui-android:1.0.0")
        assertNotNull(definition)
        assertEquals(definition.version, "1.0.0")
        assertEquals(definition.groupId, "androidx.compose.ui")
        assertEquals(definition.artifactId, "ui-android")
        assertEquals(definition.toLibraryCoordinates(), "androidx.compose.ui:ui-android")
    }

    @Test
    fun testWithSuffix() {
        val definition = collector.extractLibraryDefinition("androidx.compose.ui:ui-android:1.0.0@aarch")
        assertNotNull(definition)
        assertEquals(definition.version, "1.0.0")
        assertEquals(definition.groupId, "androidx.compose.ui")
        assertEquals(definition.artifactId, "ui-android")
        assertEquals(definition.toLibraryCoordinates(), "androidx.compose.ui:ui-android")
    }

    @Test
    fun testCombined() {
        val definition = collector.extractLibraryDefinition("Gradle: androidx.compose.ui:ui-android:1.0.0@aarch")
        assertNotNull(definition)
        assertEquals(definition.version, "1.0.0")
        assertEquals(definition.groupId, "androidx.compose.ui")
        assertEquals(definition.artifactId, "ui-android")
        assertEquals(definition.toLibraryCoordinates(), "androidx.compose.ui:ui-android")
    }

    @Test
    fun testMalformedPrefix() {
        val definition = collector.extractLibraryDefinition("Gradle:a androidx.compose.ui:ui-android:1.0.0@aarch")
        assertNull(definition)
    }

    @Test
    fun testMissingArtifactName() {
        val definition = collector.extractLibraryDefinition("Gradle: androidx.compose.ui:1.0.0@aarch")
        assertNull(definition)
    }

    @Test
    fun testMissingArtifactName2() {
        val definition = collector.extractLibraryDefinition("androidx.compose.ui:1.0.0")
        assertNull(definition)
    }

    @Test
    fun testSpaceBeforeSuffix() {
        val definition = collector.extractLibraryDefinition("androidx.compose.ui:1.0.0 @aarch")
        assertNull(definition)
    }

    @Test
    fun testSpaceInLibraryName() {
        val definition = collector.extractLibraryDefinition("androidx.compose.ui:ui -android:1.0.0")
        assertNull(definition)
    }

    @Test
    fun testMissingArtifactNameAndVersion() {
        val definition = collector.extractLibraryDefinition("androidx.compose.ui")
        assertNull(definition)
    }

    @Test
    fun testUserDefinedLibraryName() {
        val definition = collector.extractLibraryDefinition("Some UserdefinedName")
        assertNull(definition)
    }
}