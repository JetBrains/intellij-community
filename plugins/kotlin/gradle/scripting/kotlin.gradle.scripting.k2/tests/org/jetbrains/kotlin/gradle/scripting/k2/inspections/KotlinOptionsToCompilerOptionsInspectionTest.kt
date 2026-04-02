// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsOlderThan
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest

internal class KotlinOptionsToCompilerOptionsInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        projectFixture: GradleTestFixtureBuilder,
        test: () -> Unit
    ) {
        assumeThatGradleIsAtLeast(gradleVersion, "8.11")
        assumeThatGradleIsOlderThan(gradleVersion, "9.0.0")
        test(gradleVersion, projectFixture) {
            codeInsightFixture.enableInspections(KotlinOptionsToCompilerOptionsInspection::class.java)
            test()
        }
    }

    private fun createFixture(projectName: String, imports: List<String>, prefixCode: String): GradleTestFixtureBuilder {
        return GradleTestFixtureBuilder.create(projectName) { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName(projectName)
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                for (import in imports) {
                    addImport(import)
                }
                withKotlinJvmPlugin()
                withRepository { mavenCentral() }
                withPrefix {
                    code(prefixCode)
                }
            }
        }
    }

    private fun placeCaretIfNeeded(needed: Boolean): String = if (needed) "<caret>" else ""

    private fun getBuildScriptPartAllProjects(placeCaret: Boolean): String =
        """
allprojects {
    tasks.withType<KotlinCompile>().configureEach {
        ${placeCaretIfNeeded(placeCaret)}kotlinOptions.jvmTarget = "1.8"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAllProjects(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "all-projects",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartAllProjects(placeCaret = false)
            )
        ) {
            testIntention(
                getBuildScriptPartAllProjects(placeCaret = true),
                """
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

allprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartAssignmentOperation(placeCaret: Boolean): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.freeCompilerArgs += "-Xexport-kdoc"
}
                    """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAssignmentOperation(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "assignment-operation",
                emptyList(),
                getBuildScriptPartAssignmentOperation(false)
            )
        ) {
            testIntention(
                getBuildScriptPartAssignmentOperation(true),
                """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartAssignmentOperationTwoParams(placeCaret: Boolean): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.freeCompilerArgs += "-Xexport-kdoc" + "-Xopt-in=kotlin.RequiresOptIn"
}
                    """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAssignmentOperationTwoParams(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "assignment-operation-two-params",
                emptyList(),
                getBuildScriptPartAssignmentOperationTwoParams(false)
            )
        ) {
            testIntention(
                getBuildScriptPartAssignmentOperationTwoParams(true),
                """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    compilerOptions.freeCompilerArgs.addAll("-Xexport-kdoc", "-Xopt-in=kotlin.RequiresOptIn")
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartCompilerOptions(placeCaret: Boolean): String =
        """
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }

    val overriddenLanguageVersion = project.properties["kotlin.language.version"]?.toString()?.takeIf { it.isNotEmpty() }
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        if (overriddenLanguageVersion != null) {
            languageVersion = overriddenLanguageVersion
            // We replace statements even in children
            freeCompilerArgs += "-Xsuppress-version-warnings"
        }
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontMergeConvertedOptionsToAnotherCompilerOptions(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "compiler-options",
                listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartCompilerOptions(false)
            )
        ) {
            testIntention(
                getBuildScriptPartCompilerOptions(true),
                """
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }

    val overriddenLanguageVersion = project.properties["kotlin.language.version"]?.toString()?.takeIf { it.isNotEmpty() }
    compilerOptions {
        if (overriddenLanguageVersion != null) {
            languageVersion.set(KotlinVersion.fromVersion(overriddenLanguageVersion))
            // We replace statements even in children
            freeCompilerArgs.add("-Xsuppress-version-warnings")
        }
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartForbiddenOperation(placeCaret: Boolean): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.freeCompilerArgs -= "-Xexport-kdoc"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceIfForbiddenOperation(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "forbidden-operation",
                emptyList(),
                getBuildScriptPartForbiddenOperation(false)
            )
        ) {
            testNoIntentions(
                getBuildScriptPartForbiddenOperation(true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartForbiddenOperation2(placeCaret: Boolean): String =
        """
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        freeCompilerArgs -= "-Xexport-kdoc"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceIfForbiddenOperation2(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "forbidden-operation-2",
                emptyList(),
                getBuildScriptPartForbiddenOperation2(false)
            )
        ) {
            testNoIntentions(
                getBuildScriptPartForbiddenOperation2(true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private val EMPTY_BUILD_SCRIPT_FIXTURE =
        GradleTestFixtureBuilder.create("empty-build-script") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("empty-build-script")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinJvmPlugin()
                withRepository { mavenCentral() }
            }
            withFile("src/main/kotlin/Test.kt", getKotlinFileContent(false))
        }

    private fun getKotlinFileContent(placeCaret: Boolean): String =
        """
class Test{
    var parameter = 0
}

fun main() {
    val kotlinOptions = Test()
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.parameter = 1
}
            """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceInKotlinFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_BUILD_SCRIPT_FIXTURE) {
            testNoIntentions(
                "src/main/kotlin/Test.kt",
                getKotlinFileContent(true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private val WITH_KOTLIN_OPTIONS_IN_SETTINGS_FILE_FIXTURE =
        GradleTestFixtureBuilder.create("with-kotlin-options-in-settings-file") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                addCode(getSettingsFileContent(false))
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinJvmPlugin()
                withRepository { mavenCentral() }
            }
        }

    private fun getSettingsFileContent(placeCaret: Boolean): String =
        """
rootProject.name = "with-kotlin-options-in-settings-file"

val kotlinOptions = mapOf("jvmTarget" to "")
${placeCaretIfNeeded(placeCaret)}kotlinOptions["jvmTarget"]
            """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceInSettingsGradle(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_KOTLIN_OPTIONS_IN_SETTINGS_FILE_FIXTURE) {
            testNoIntentions(
                "settings.gradle.kts",
                getSettingsFileContent(true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMinusOperator1(placeCaret: Boolean): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
            freeCompilerArgs = freeCompilerArgs - "-Xopt-in=kotlin.RequiresOptIn"
        }
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceWithMinusOperator1(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "minus-operator-1",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMinusOperator1(false)
            )
        ) {
            testNoIntentions(
                getBuildScriptPartMinusOperator1(true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMinusOperator2(placeCaret: Boolean, caretOnRightSide: Boolean = false): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${if (placeCaret && !caretOnRightSide) "<caret>" else ""}kotlinOptions.freeCompilerArgs = ${if (placeCaret && caretOnRightSide) "<caret>" else ""}kotlinOptions.freeCompilerArgs - "-Xopt-in=kotlin.RequiresOptIn"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceWithMinusOperator2(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "minus-operator-2",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMinusOperator2(placeCaret = false)
            )
        ) {
            testNoIntentions(
                getBuildScriptPartMinusOperator2(placeCaret = true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    @Disabled("KTIJ-38171")
    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceWithMinusOperatorAndExpressionOnTheRightSide1(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "minus-operator-2",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMinusOperator2(placeCaret = false)
            )
        ) {
            testNoIntentions(
                getBuildScriptPartMinusOperator2(placeCaret = true, caretOnRightSide = true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMinusOperator3(placeCaret: Boolean, caretOnRightSide: Boolean = false): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${if (placeCaret && !caretOnRightSide) "<caret>" else ""}kotlinOptions.freeCompilerArgs =
            ${if (placeCaret && caretOnRightSide) "<caret>" else ""}kotlinOptions.freeCompilerArgs - "-Xopt-in=kotlin.RequiresOptIn" + "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceWithMinusOperator3(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "minus-operator-3",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMinusOperator3(placeCaret = false)
            )
        ) {
            testNoIntentions(
                getBuildScriptPartMinusOperator3(placeCaret = true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    @Disabled("KTIJ-38171")
    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceWithMinusOperatorAndExpressionOnTheRightSide2(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "minus-operator-3",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMinusOperator3(placeCaret = false)
            )
        ) {
            testNoIntentions(
                getBuildScriptPartMinusOperator3(placeCaret = true, caretOnRightSide = true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartAddAllFromList(placeCaret: Boolean): String =
        """
tasks.withType<KotlinCompile> {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        freeCompilerArgs += listOf("-module-name", "TheName")
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsAddAllFromList(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "add-all-from-list",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartAddAllFromList(false)
            )
        ) {
            testIntention(
                getBuildScriptPartAddAllFromList(true),
                """
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(listOf("-module-name", "TheName"))
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition1(placeCaret: Boolean): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn" + "-Xjvm-default=all"
        }
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsMultipleAddition1(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "multiple-addition-1",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMultipleAddition1(false)
            )
        ) {
            testIntention(
                getBuildScriptPartMultipleAddition1(true),
                """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs.addAll("-Xopt-in=kotlin.RequiresOptIn", "-Xjvm-default=all")
        }
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition2(placeCaret: Boolean): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + project.properties.get("A").toString() + project.properties.get("B").toString()
        }
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsMultipleAddition2(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "multiple-addition-2",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMultipleAddition2(false)
            )
        ) {
            testIntention(
                getBuildScriptPartMultipleAddition2(true),
                """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs.addAll(project.properties.get("A").toString(), project.properties.get("B").toString())
        }
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition3(placeCaret: Boolean): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
            freeCompilerArgs = freeCompilerArgs +
                    "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi" +
                    "-opt-in=androidx.compose.animation.ExperimentalAnimationApi" +
                    "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api" +
                    "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi"
        }
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsMultipleAddition3(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "multiple-addition-3",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMultipleAddition3(false)
            )
        ) {
            testIntention(
                getBuildScriptPartMultipleAddition3(true),
                """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi"
            )
        }
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition4(placeCaret: Boolean): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
            freeCompilerArgs += libraries.flatMap { listOf("-include-binary", it.path) } + "-include-binary, junit"
        }
    }
}
                """.trimIndent()

    @Disabled("KTIJ-38174")
    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsMultipleAddition4(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "multiple-addition-4",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMultipleAddition4(false)
            )
        ) {
            testIntention(
                getBuildScriptPartMultipleAddition4(true),
                """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs.addAll(libraries.flatMap { listOf("-include-binary", it.path) }, "-include-binary, junit")
        }
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition5(placeCaret: Boolean): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xopt-in=kotlinx.coroutines.FlowPreview",
                "-Xopt-in=kotlin.time.ExperimentalTime",
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all",
                "-XXLanguage:+DataObjects",
                "-Xcontext-receivers"
            )
        }
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsMultipleAddition5(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "multiple-addition-5",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartMultipleAddition5(false)
            )
        ) {
            testIntention(
                getBuildScriptPartMultipleAddition5(true),
                """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs.addAll(
                listOf(
                    "-Xopt-in=kotlin.RequiresOptIn",
                    "-Xopt-in=kotlin.ExperimentalStdlibApi",
                    "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-Xopt-in=kotlinx.coroutines.FlowPreview",
                    "-Xopt-in=kotlin.time.ExperimentalTime",
                    "-Xopt-in=kotlin.RequiresOptIn",
                    "-Xjvm-default=all",
                    "-XXLanguage:+DataObjects",
                    "-Xcontext-receivers"
                )
            )
        }
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartFreeCompilerArgs(placeCaret: Boolean): String =
        """
tasks.withType<KotlinCompile> {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsPlusFreeCompilerArgs(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "free-compiler-args",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartFreeCompilerArgs(false)
            )
        ) {
            testIntention(
                getBuildScriptPartFreeCompilerArgs(true),
                """
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xopt-in=kotlin.RequiresOptIn")
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartFreeCompilerArgsSetList(placeCaret: Boolean): String =
        """
tasks.withType<KotlinCompile> {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsSetList(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "free-compiler-args-set-list",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartFreeCompilerArgsSetList(false)
            )
        ) {
            testIntention(
                getBuildScriptPartFreeCompilerArgsSetList(true),
                """
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartFreeCompilerArgsWithSuppress(placeCaret: Boolean): String =
        """
tasks.withType<KotlinCompile> {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
        // KtExpressionImpl performs replaceExpression() and there calls KtPsiUtil.areParenthesesNecessary(). Inside, innerPriority is calculated
        // for DOT_QUALIFIED_EXPRESSION and it's 14, parentPriority is calculated for ANNOTATED_EXPRESSION is 15, and that's why () are added
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsWithSuppress(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "free-compiler-args-with-suppress",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartFreeCompilerArgsWithSuppress(false)
            )
        ) {
            testIntention(
                getBuildScriptPartFreeCompilerArgsWithSuppress(true),
                """
tasks.withType<KotlinCompile> {
    compilerOptions {
        @Suppress("SuspiciousCollectionReassignment")
        (freeCompilerArgs.addAll(listOf("-Xopt-in=kotlin.RequiresOptIn")))
        // KtExpressionImpl performs replaceExpression() and there calls KtPsiUtil.areParenthesesNecessary(). Inside, innerPriority is calculated
        // for DOT_QUALIFIED_EXPRESSION and it's 14, parentPriority is calculated for ANNOTATED_EXPRESSION is 15, and that's why () are added
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJava11(placeCaret: Boolean): String =
        """
val compileKotlin: KotlinCompile by tasks
compileKotlin.${placeCaretIfNeeded(placeCaret)}kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJava11(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "java-11",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartJava11(false)
            )
        ) {
            testIntention(
                getBuildScriptPartJava11(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJavaVersionDefinedSeparately(placeCaret: Boolean): String =
        """
val javaVersion = JavaVersion.VERSION_1_8
tasks.withType(KotlinJvmCompile::class.java).configureEach {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        jvmTarget = javaVersion.toString()
        freeCompilerArgs += setOf(
            "-Xjvm-default=all",
        )
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJavaVersionDefinedSeparately(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "java-version-defined-separately",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile"),
                getBuildScriptPartJavaVersionDefinedSeparately(false)
            )
        ) {
            testIntention(
                getBuildScriptPartJavaVersionDefinedSeparately(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val javaVersion = JavaVersion.VERSION_1_8
tasks.withType(KotlinJvmCompile::class.java).configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        freeCompilerArgs.addAll(
            setOf(
                "-Xjvm-default=all",
            )
        )
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJsSourceMapEmbedSources(placeCaret: Boolean): String =
        """
tasks.withType<Kotlin2JsCompile>().configureEach {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.sourceMapEmbedSources = "inlining"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJsSourceMapEmbedSources(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "js-source-map-embed-sources",
                listOf("org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile"),
                getBuildScriptPartJsSourceMapEmbedSources(false)
            )
        ) {
            testIntention(
                getBuildScriptPartJsSourceMapEmbedSources(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode

tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions.sourceMapEmbedSources.set(JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_INLINING)
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJsSourceMapNamesPolicy(placeCaret: Boolean): String =
        """
tasks.withType<Kotlin2JsCompile>().configureEach {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.sourceMapNamesPolicy = "fully-qualified-names"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJsSourceMapNamesPolicy(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "js-source-map-names-policy",
                listOf("org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile"),
                getBuildScriptPartJsSourceMapNamesPolicy(false)
            )
        ) {
            testIntention(
                getBuildScriptPartJsSourceMapNamesPolicy(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy

tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions.sourceMapNamesPolicy.set(JsSourceMapNamesPolicy.SOURCE_MAP_NAMES_POLICY_FQ_NAMES)
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartTypeOfFqnAndConfigureEach(placeCaret: Boolean): String =
        """
tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.main = "noCall"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJsTasksWithTypeOfFQNandConfigureEach(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "type-of-fqn-and-configure-each",
                emptyList(),
                getBuildScriptPartTypeOfFqnAndConfigureEach(false)
            )
        ) {
            testIntention(
                getBuildScriptPartTypeOfFqnAndConfigureEach(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode

tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
    compilerOptions.main.set(JsMainFunctionExecutionMode.NO_CALL)
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJsTestOrdinaryStringOption(placeCaret: Boolean): String =
        """
tasks.withType<Kotlin2JsCompile>().configureEach {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.sourceMapPrefix = "myPrefix"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJsTestOrdinaryStringOption(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "js-test-ordinary-string-option",
                listOf("org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile"),
                getBuildScriptPartJsTestOrdinaryStringOption(false)
            )
        ) {
            testIntention(
                getBuildScriptPartJsTestOrdinaryStringOption(true),
                """
tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions.sourceMapPrefix.set("myPrefix")
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJvmTarget9(placeCaret: Boolean): String =
        """
val compileKotlin: KotlinCompile by tasks
compileKotlin.${placeCaretIfNeeded(placeCaret)}kotlinOptions {
    jvmTarget = "9"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJvmTarget9(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "jvm-target-9",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartJvmTarget9(false)
            )
        ) {
            testIntention(
                getBuildScriptPartJvmTarget9(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_9)
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJvmTargetDefinedWithEnum(placeCaret: Boolean): String =
        """
val compileKotlin: KotlinCompile by tasks
compileKotlin.${placeCaretIfNeeded(placeCaret)}kotlinOptions {
    jvmTarget = JavaVersion.VERSION_1_8.toString()
    languageVersion = LanguageVersion.KOTLIN_2_1.toString()
    apiVersion = ApiVersion.KOTLIN_2_1.toString()
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJvmTargetDefinedWithEnum(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "jvm-target-defined-with-enum",
                listOf(
                    "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
                    "org.jetbrains.kotlin.config.ApiVersion",
                    "org.jetbrains.kotlin.config.LanguageVersion"
                ),
                getBuildScriptPartJvmTargetDefinedWithEnum(false)
            )
        ) {
            testIntention(
                getBuildScriptPartJvmTargetDefinedWithEnum(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    languageVersion.set(KotlinVersion.fromVersion(LanguageVersion.KOTLIN_2_1.toString()))
    apiVersion.set(KotlinVersion.fromVersion(ApiVersion.KOTLIN_2_1.toString()))
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private val WITH_KOTLIN_OPTIONS_WITH_JVM_TARGET_SETTING_WITH_PROPERTIES_FIXTURE =
        GradleTestFixtureBuilder.create("with-kotlin-options-with-jvm-target-setting-with-properties") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("with-kotlin-options-with-jvm-target-setting-with-properties")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                addImport("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                withKotlinJvmPlugin()
                withRepository { mavenCentral() }
                withPrefix {
                    code(
                        getBuildScriptPartWithCallingProperties(false)
                    )
                }
            }
            withFile(
                "gradle.properties", /* language=TOML */ """
                    javaVersion=1.8
                    """.trimIndent()
            )
        }

    private fun getBuildScriptPartWithCallingProperties(placeCaret: Boolean): String =
        """
fun properties(key: String) = project.findProperty(key).toString()

val compileKotlin: KotlinCompile by tasks
compileKotlin.${placeCaretIfNeeded(placeCaret)}kotlinOptions {
    jvmTarget = properties("javaVersion")
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = properties("javaVersion")
}
                    """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJvmTargetSettingWithProperties(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_KOTLIN_OPTIONS_WITH_JVM_TARGET_SETTING_WITH_PROPERTIES_FIXTURE) {
            testIntention(
                getBuildScriptPartWithCallingProperties(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun properties(key: String) = project.findProperty(key).toString()

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(properties("javaVersion")))
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = properties("javaVersion")
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartApiVersionAsString(placeCaret: Boolean): String =
        """
val compileKotlin: KotlinCompile by tasks
compileKotlin.${placeCaretIfNeeded(placeCaret)}kotlinOptions {
    jvmTarget = "9"
    freeCompilerArgs += listOf("-module-name", "TheName")
    apiVersion = "1.9"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testApiVersionAsString(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "api-version-as-string",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartApiVersionAsString(false)
            )
        ) {
            testIntention(
                getBuildScriptPartApiVersionAsString(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_9)
    freeCompilerArgs.addAll(listOf("-module-name", "TheName"))
    apiVersion.set(KotlinVersion.KOTLIN_1_9)
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartOptionsBeforeDot(placeCaret: Boolean): String =
        """
tasks.withType<KotlinCompile>().configureEach {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions { options.jvmTarget.set(JvmTarget.JVM_11) }
}
                """.trimIndent()

    @Disabled("KTIJ-38181") // The "After" part should be fixed don't know yet how
    @ParameterizedTest
    @AllGradleVersionsSource
    fun testOptionsBeforeDot(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "options-before-dot",
                listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartOptionsBeforeDot(false)
            )
        ) {
            testIntention(
                getBuildScriptPartOptionsBeforeDot(true),
                """
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { options.jvmTarget.set(JvmTarget.JVM_11) }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartOptionsBeforeDotInDotQualifiedExpression(placeCaret: Boolean): String =
        """
tasks.withType<KotlinCompile>().configureEach {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.options.jvmTarget.set(JvmTarget.JVM_11)
}
                """.trimIndent()

    @Disabled("KTIJ-38181") // The "After" part should be fixed don't know yet how
    @ParameterizedTest
    @AllGradleVersionsSource
    fun testOptionsBeforeDotInDotQualifiedExpression(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "options-before-dot-in-dot-qualified-expression",
                listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartOptionsBeforeDotInDotQualifiedExpression(false)
            )
        ) {
            testIntention(
                getBuildScriptPartOptionsBeforeDotInDotQualifiedExpression(true),
                """
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.options.jvmTarget.set(JvmTarget.JVM_11)
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartWithSubprojects(placeCaret: Boolean): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "11"
        }
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testWithSubprojects(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "subprojects",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartWithSubprojects(false)
            )
        ) {
            testIntention(
                getBuildScriptPartWithSubprojects(true),
                """
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs.set(listOf("-Xjsr305=strict"))
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartGetByName(placeCaret: Boolean): String =
        """
tasks.getByName<KotlinCompile>("compileKotlin") {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions.allWarningsAsErrors = true
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testGetByName(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "get-by-name",
                listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
                getBuildScriptPartGetByName(false)
            )
        ) {
            testIntention(
                getBuildScriptPartGetByName(true),
                """
tasks.getByName<KotlinCompile>("compileKotlin") {
    compilerOptions.allWarningsAsErrors.set(true)
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartGetByNameAndDotReferenced(placeCaret: Boolean): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        languageVersion = "1.9"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testGetByNameAndDotReferenced(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "get-by-name-and-dot-referenced",
                emptyList(),
                getBuildScriptPartGetByNameAndDotReferenced(false)
            )
        ) {
            testIntention(
                getBuildScriptPartGetByNameAndDotReferenced(true),
                """
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartGetByNameAndLambda(placeCaret: Boolean): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    ${placeCaretIfNeeded(placeCaret)}kotlinOptions {
        languageVersion = "1.9"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testGetByNameAndLambda(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            createFixture(
                "get-by-name-and-lambda",
                emptyList(),
                getBuildScriptPartGetByNameAndLambda(false)
            )
        ) {
            testIntention(
                getBuildScriptPartGetByNameAndLambda(true),
                """
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}
                """.trimIndent(),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

}
