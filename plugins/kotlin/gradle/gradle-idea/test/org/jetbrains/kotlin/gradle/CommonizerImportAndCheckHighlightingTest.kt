package org.jetbrains.kotlin.gradle

import com.intellij.openapi.roots.DependencyScope.COMPILE
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import java.io.PrintStream

class CommonizerImportAndCheckHighlightingTest : MultiplePluginVersionGradleImportingTestCase() {

    override fun testDataDirName(): String = "commonizerImportAndCheckHighlighting"

    override fun printOutput(stream: PrintStream, text: String) = stream.println(text)

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.20-M1+")
    fun testWithPosix() {
        configureByFiles()
        importProject(false)
        val highlightingCheck = createHighlightingCheck()

        checkProjectStructure(false, false, false) {
            module("project.p1.nativeMain") {
                highlightingCheck(module)
                libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                libraryDependencyByUrl(Regex(""".*posix.*"""), COMPILE)
            }

            module("project.p1.appleAndLinuxMain") {
                if (SystemInfo.isMac || SystemInfo.isLinux) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), COMPILE)
                }
            }

            module("project.p1.linuxMain") {
                if (SystemInfo.isMac || SystemInfo.isLinux) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), COMPILE)
                }
            }

            module("project.p1.appleMain") {
                if (SystemInfo.isMac) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), COMPILE)
                }
            }

            module("project.p1.iosMain") {
                if (SystemInfo.isMac) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), COMPILE)
                }
            }

            module("project.p1.linuxArm64Main") {
                if (SystemInfo.isMac || SystemInfo.isLinux) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*/\(linux_arm64, linux_x64\)/.*posix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*/linux_arm64/.*posix.*"""), COMPILE)
                }
            }

            module("project.p1.linuxX64Main") {
                if (SystemInfo.isMac || SystemInfo.isLinux) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*/\(linux_arm64, linux_x64\)/.*posix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*/linux_x64/.*posix.*"""), COMPILE)
                }
            }

            module("project.p1.macosMain") {
                if (SystemInfo.isMac) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*/\(.*macos_x64.*\)/.*posix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*/macos_x64/.*posix.*"""), COMPILE)
                }
            }

            module("project.p1.windowsMain") {
                if (SystemInfo.isWindows) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), COMPILE)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), COMPILE)
                }
            }
        }
    }
}
