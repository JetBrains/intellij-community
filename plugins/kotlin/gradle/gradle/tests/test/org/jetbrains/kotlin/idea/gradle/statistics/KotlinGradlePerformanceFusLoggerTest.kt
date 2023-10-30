package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.util.io.copy
import org.jetbrains.kotlin.idea.gradle.statistics.v2.flow.KotlinBuildToolFusFlowProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.assertEquals

class KotlinGradlePerformanceFusLoggerTest {
    @Test
    fun testFileFilter(@TempDir tempDir: Path) {
        val kotlinProfileDir  = tempDir.createDirectory("kotlin-profile")
        Path("resources/kotlin-profile/build_id_1.finish-profile").copy(kotlinProfileDir.resolve("build_id_1.finish-profile"))
        Path("resources/kotlin-profile/build_id_1.plugin-profile").copy(kotlinProfileDir.resolve("build_id_1-ksjfsjfhbv.plugin-profile"))
        Path("resources/kotlin-profile/build_id_2.plugin-profile").copy(kotlinProfileDir.resolve("build_id_2-kajbsjvbjb.plugin-profile"))

        val anotherDir = tempDir.createDirectory("another-dir")
        Path("resources/kotlin-profile/build_id_3.finish-profile").copy(anotherDir.resolve("build_id_3.finish-profile"))
        Path("resources/kotlin-profile/build_id_3.plugin-profile").copy(anotherDir.resolve("build_id_3-kjsbfsljbfsjkhb.plugin-profile"))

        val pathsByBuildIds = KotlinBuildToolFusFlowProcessor.filterFilesToRead(tempDir.absolutePathString())
        assertEquals(setOf("build_id_1"), pathsByBuildIds.keys)
        val paths = pathsByBuildIds["build_id_1"]?.map { it.absolutePathString() } ?: emptyList()
        assertEquals(listOf(kotlinProfileDir.resolve("build_id_1-ksjfsjfhbv.plugin-profile").absolutePathString(), kotlinProfileDir.resolve("build_id_1.finish-profile").absolutePathString()), paths)
    }

    @Test
    fun testInnerDirectories(@TempDir tempDir: Path) {
        val kotlinProfileDir  = tempDir.createDirectory("kotlin-profile")
        Path("resources/kotlin-profile/build_id_1.finish-profile").copy(kotlinProfileDir.resolve("build_id_1.finish-profile"))
        Path("resources/kotlin-profile/build_id_1.plugin-profile").copy(kotlinProfileDir.resolve("build_id_1.ksjfsjfhbv.plugin-profile"))

        val innerDir = kotlinProfileDir.createDirectory("inner-dir")
        Path("resources/kotlin-profile/build_id_2.plugin-profile").copy(innerDir.resolve("build_id_2.kajbsjvbjb.plugin-profile"))
        Path("resources/kotlin-profile/build_id.finish-profile").copy(innerDir.resolve("build_id_2.finish-profile"))

        val pathsByBuildIds = KotlinBuildToolFusFlowProcessor.filterFilesToRead(tempDir.absolutePathString())
        assertEquals(setOf("build_id_1"), pathsByBuildIds.keys)
    }

    @Test
    fun testOldFiles(@TempDir tempDir: Path) {
        val buildId = "6b21801c-c13c-4971-b538-90bb76346832"
        val kotlinProfileDir  = tempDir.createDirectory("kotlin-profile")
        Path("resources/kotlin-profile/build_id.finish-profile").copy(kotlinProfileDir.resolve("$buildId.finish-profile"))
        Path("resources/kotlin-profile/build_id_1.plugin-profile").copy(kotlinProfileDir.resolve("$buildId.profile"))

        val pathsByBuildIds = KotlinBuildToolFusFlowProcessor.filterFilesToRead(tempDir.absolutePathString())
        assertEquals(setOf(buildId), pathsByBuildIds.keys)
        assertEquals(1, pathsByBuildIds[buildId]?.size)
    }

}