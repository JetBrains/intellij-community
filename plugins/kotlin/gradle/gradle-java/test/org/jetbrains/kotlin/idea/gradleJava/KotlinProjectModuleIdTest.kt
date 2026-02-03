package org.jetbrains.kotlin.idea.gradleJava

import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectCoordinates
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinProjectModuleId
import org.jetbrains.kotlin.tooling.core.UnsafeApi
import org.junit.Assert.assertEquals
import kotlin.test.Test

@OptIn(UnsafeApi::class)
@Suppress("DEPRECATION")
class KotlinProjectModuleIdTest {

    @Test
    fun `test - root build - root project`() {
        val coordinates = IdeaKotlinProjectCoordinates(
            buildId = ":", projectPath = ":", projectName = "myProjectName"
        )

        assertEquals(
            KotlinProjectModuleId("myProjectName"),
            KotlinProjectModuleId(coordinates)
        )
    }

    @Test
    fun `test - root buildPath - root project`() {
        val coordinates = IdeaKotlinProjectCoordinates(
            buildPath = ":", buildName = ":", projectPath = ":", projectName = "myProjectName"
        )

        assertEquals(
            KotlinProjectModuleId("myProjectName"),
            KotlinProjectModuleId(coordinates)
        )
    }

    @Test
    fun `test - root build - subproject`() {
        val coordinates = IdeaKotlinProjectCoordinates(
            buildId = ":", projectPath = ":subproject", projectName = "subprojectName"
        )

        assertEquals(
            KotlinProjectModuleId(":subproject"),
            KotlinProjectModuleId(coordinates)
        )
    }


    @Test
    fun `test - root build path - subproject`() {
        val coordinates = IdeaKotlinProjectCoordinates(
            buildPath = ":", buildName = ":", projectPath = ":subproject", projectName = "subprojectName"
        )

        assertEquals(
            KotlinProjectModuleId(":subproject"),
            KotlinProjectModuleId(coordinates)
        )
    }
    @Test
    fun `test - included build - root project`() {
        val coordinates = IdeaKotlinProjectCoordinates(
            buildId = "included-build", projectPath = ":", projectName = "includedBuild"
        )

        assertEquals(
            KotlinProjectModuleId(":included-build"),
            KotlinProjectModuleId(coordinates)
        )
    }

    @Test
    fun `test - included buildPath - root project`() {
        val coordinates = IdeaKotlinProjectCoordinates(
            buildPath = ":included-build", buildName = "included-build", projectPath = ":", projectName = "includedBuild"
        )

        assertEquals(
            KotlinProjectModuleId(":included-build"),
            KotlinProjectModuleId(coordinates)
        )
    }

    @Test
    fun `test - included build - subproject`() {
        val coordinates = IdeaKotlinProjectCoordinates(
            buildId = "included-build", projectPath = ":subproject", projectName = "subprojectName"
        )

        assertEquals(
            KotlinProjectModuleId(":included-build:subproject"),
            KotlinProjectModuleId(coordinates)
        )
    }

    @Test
    fun `test - included buildPath - subproject`() {
        val coordinates = IdeaKotlinProjectCoordinates(
            buildPath = ":included-build", buildName = "included-build", projectPath = ":subproject", projectName = "subprojectName"
        )

        assertEquals(
            KotlinProjectModuleId(":included-build:subproject"),
            KotlinProjectModuleId(coordinates)
        )
    }
}