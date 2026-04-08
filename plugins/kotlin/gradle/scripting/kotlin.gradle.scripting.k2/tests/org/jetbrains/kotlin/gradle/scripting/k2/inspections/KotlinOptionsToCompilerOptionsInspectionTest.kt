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

    private val BASIC_PROJECT_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("empty-project") { gradleVersion ->
        withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
            setProjectName("empty-project")
            addCode(getSettingsFileContent(placeCaret = false))
        }
        withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
            withKotlinJvmPlugin()
            withRepository { mavenCentral() }
        }
        withFile(
            "gradle.properties", /* language=TOML */ """
                    javaVersion=1.8
                    """.trimIndent()
        )
        withFile("src/main/kotlin/Test.kt", getKotlinFileContent(placeCaret = false))
    }

    private fun createBuildScript(scriptBody: String, imports: List<String> = emptyList()): String {
        val sections = mutableListOf<String>()
        if (imports.isNotEmpty()) {
            sections += imports.joinToString("\n") { "import $it" }
        }
        sections += """
plugins {
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
}
        """.trimIndent()
        sections += scriptBody.trimIndent()
        return sections.joinToString("\n\n")
    }

    private fun placeCaretIfNeeded(needed: Boolean): String = if (needed) "<caret>" else ""

    private fun getBuildScriptPartAllProjects(): String =
        """
allprojects {
    tasks.withType<KotlinCompile>().configureEach {
        <caret>kotlinOptions.jvmTarget = "1.8"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAllProjects(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(getBuildScriptPartAllProjects(), listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")),
                createBuildScript(
                    """
allprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
    }
}
                """.trimIndent(), listOf(
                        "org.jetbrains.kotlin.gradle.dsl.JvmTarget",
                        "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"
                    )
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartAssignmentOperation(): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    <caret>kotlinOptions.freeCompilerArgs += "-Xexport-kdoc"
}
                    """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAssignmentOperation(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(getBuildScriptPartAssignmentOperation()),
                createBuildScript(
                    """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
}
                """.trimIndent()
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartAssignmentOperationTwoParams(): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    <caret>kotlinOptions.freeCompilerArgs += "-Xexport-kdoc" + "-Xopt-in=kotlin.RequiresOptIn"
}
                    """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAssignmentOperationTwoParams(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(getBuildScriptPartAssignmentOperationTwoParams()),
                createBuildScript(
                    """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    compilerOptions.freeCompilerArgs.addAll("-Xexport-kdoc", "-Xopt-in=kotlin.RequiresOptIn")
}
                    """.trimIndent()
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartCompilerOptions(): String =
        """
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }

    val overriddenLanguageVersion = project.properties["kotlin.language.version"]?.toString()?.takeIf { it.isNotEmpty() }
    <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartCompilerOptions(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
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
                    listOf(
                        "org.jetbrains.kotlin.gradle.dsl.JvmTarget",
                        "org.jetbrains.kotlin.gradle.dsl.KotlinVersion",
                        "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"
                    )
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartForbiddenOperation(): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    <caret>kotlinOptions.freeCompilerArgs -= "-Xexport-kdoc"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceIfForbiddenOperation(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testNoIntentions(
                createBuildScript(getBuildScriptPartForbiddenOperation()),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartForbiddenOperation2(): String =
        """
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    <caret>kotlinOptions {
        freeCompilerArgs -= "-Xexport-kdoc"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceIfForbiddenOperation2(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testNoIntentions(
                createBuildScript(getBuildScriptPartForbiddenOperation2()),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
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
        runTest(gradleVersion, BASIC_PROJECT_FIXTURE) {
            testNoIntentions(
                "src/main/kotlin/Test.kt",
                getKotlinFileContent(placeCaret = true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getSettingsFileContent(placeCaret: Boolean): String =
        """
val kotlinOptions = mapOf("jvmTarget" to "")
${placeCaretIfNeeded(placeCaret)}kotlinOptions["jvmTarget"]
            """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceInSettingsGradle(gradleVersion: GradleVersion) {
        runTest(gradleVersion, BASIC_PROJECT_FIXTURE) {
            testNoIntentions(
                "settings.gradle.kts",
                getSettingsFileContent(true),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMinusOperator1(): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testNoIntentions(
                createBuildScript(
                    getBuildScriptPartMinusOperator1(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMinusOperator2(caretOnRightSide: Boolean = false): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${if (!caretOnRightSide) "<caret>" else ""}kotlinOptions.freeCompilerArgs = ${if (caretOnRightSide) "<caret>" else ""}kotlinOptions.freeCompilerArgs - "-Xopt-in=kotlin.RequiresOptIn"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceWithMinusOperator2(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testNoIntentions(
                createBuildScript(
                    getBuildScriptPartMinusOperator2(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
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
            BASIC_PROJECT_FIXTURE
        ) {
            testNoIntentions(
                createBuildScript(
                    getBuildScriptPartMinusOperator2(caretOnRightSide = true),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMinusOperator3(caretOnRightSide: Boolean = false): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        ${if (!caretOnRightSide) "<caret>" else ""}kotlinOptions.freeCompilerArgs =
            ${if (caretOnRightSide) "<caret>" else ""}kotlinOptions.freeCompilerArgs - "-Xopt-in=kotlin.RequiresOptIn" + "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDontReplaceWithMinusOperator3(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testNoIntentions(
                createBuildScript(
                    getBuildScriptPartMinusOperator3(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
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
            BASIC_PROJECT_FIXTURE
        ) {
            testNoIntentions(
                createBuildScript(
                    getBuildScriptPartMinusOperator3(caretOnRightSide = true),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartAddAllFromList(): String =
        """
tasks.withType<KotlinCompile> {
    <caret>kotlinOptions {
        freeCompilerArgs += listOf("-module-name", "TheName")
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsAddAllFromList(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartAddAllFromList(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(listOf("-module-name", "TheName"))
    }
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition1(): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartMultipleAddition1(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
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
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition2(): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartMultipleAddition2(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
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
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition3(): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartMultipleAddition3(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
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
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition4(): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartMultipleAddition4(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
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
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartMultipleAddition5(): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartMultipleAddition5(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
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
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartFreeCompilerArgs(): String =
        """
tasks.withType<KotlinCompile> {
    <caret>kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsPlusFreeCompilerArgs(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartFreeCompilerArgs(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xopt-in=kotlin.RequiresOptIn")
    }
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartFreeCompilerArgsSetList(): String =
        """
tasks.withType<KotlinCompile> {
    <caret>kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFreeCompilerArgsSetList(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartFreeCompilerArgsSetList(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
    }
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartFreeCompilerArgsWithSuppress(): String =
        """
tasks.withType<KotlinCompile> {
    <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartFreeCompilerArgsWithSuppress(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
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
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJava11(): String =
        """
val compileKotlin: KotlinCompile by tasks
compileKotlin.<caret>kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJava11(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartJava11(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJavaVersionDefinedSeparately(): String =
        """
val javaVersion = JavaVersion.VERSION_1_8
tasks.withType(KotlinJvmCompile::class.java).configureEach {
    <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartJavaVersionDefinedSeparately(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile")
                ),
                createBuildScript(
                    """
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
                    listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJsSourceMapEmbedSources(): String =
        """
tasks.withType<Kotlin2JsCompile>().configureEach {
    <caret>kotlinOptions.sourceMapEmbedSources = "inlining"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJsSourceMapEmbedSources(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartJsSourceMapEmbedSources(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile")
                ),
                createBuildScript(
                    """
tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions.sourceMapEmbedSources.set(JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_INLINING)
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode", "org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJsSourceMapNamesPolicy(): String =
        """
tasks.withType<Kotlin2JsCompile>().configureEach {
    <caret>kotlinOptions.sourceMapNamesPolicy = "fully-qualified-names"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJsSourceMapNamesPolicy(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartJsSourceMapNamesPolicy(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile")
                ),
                createBuildScript(
                    """
tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions.sourceMapNamesPolicy.set(JsSourceMapNamesPolicy.SOURCE_MAP_NAMES_POLICY_FQ_NAMES)
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy", "org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartTypeOfFqnAndConfigureEach(): String =
        """
tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
    <caret>kotlinOptions.main = "noCall"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJsTasksWithTypeOfFQNandConfigureEach(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(getBuildScriptPartTypeOfFqnAndConfigureEach()),
                createBuildScript(
                    """
tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
    compilerOptions.main.set(JsMainFunctionExecutionMode.NO_CALL)
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJsTestOrdinaryStringOption(): String =
        """
tasks.withType<Kotlin2JsCompile>().configureEach {
    <caret>kotlinOptions.sourceMapPrefix = "myPrefix"
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJsTestOrdinaryStringOption(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartJsTestOrdinaryStringOption(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile")
                ),
                createBuildScript(
                    """
tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions.sourceMapPrefix.set("myPrefix")
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJvmTarget9() = """
val compileKotlin: KotlinCompile by tasks
compileKotlin.<caret>kotlinOptions {
    jvmTarget = "9"
}
    """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testJvmTarget9(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(getBuildScriptPartJvmTarget9(), listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")),
                createBuildScript(
                    """
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_9)
}
                """.trimIndent(), listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartJvmTargetDefinedWithEnum(): String =
        """
                    val compileKotlin: KotlinCompile by tasks
                    compileKotlin.<caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartJvmTargetDefinedWithEnum(), listOf(
                        "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
                        "org.jetbrains.kotlin.config.ApiVersion",
                        "org.jetbrains.kotlin.config.LanguageVersion"
                    )
                ),
                createBuildScript(
                    """
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    languageVersion.set(KotlinVersion.fromVersion(LanguageVersion.KOTLIN_2_1.toString()))
    apiVersion.set(KotlinVersion.fromVersion(ApiVersion.KOTLIN_2_1.toString()))
}
                """.trimIndent(), listOf(
                        "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
                        "org.jetbrains.kotlin.config.ApiVersion",
                        "org.jetbrains.kotlin.config.LanguageVersion",
                        "org.jetbrains.kotlin.gradle.dsl.JvmTarget",
                        "org.jetbrains.kotlin.gradle.dsl.KotlinVersion"
                    )
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartWithCallingProperties(): String =
        """
fun properties(key: String) = project.findProperty(key).toString()

val compileKotlin: KotlinCompile by tasks
compileKotlin.<caret>kotlinOptions {
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
        runTest(gradleVersion, BASIC_PROJECT_FIXTURE) {
            testIntention(
                createBuildScript(getBuildScriptPartWithCallingProperties(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")),
                createBuildScript(
                    """
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
                    listOf(
                        "org.jetbrains.kotlin.gradle.dsl.JvmTarget",
                        "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"
                    )
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartApiVersionAsString(): String =
        """
val compileKotlin: KotlinCompile by tasks
compileKotlin.<caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartApiVersionAsString(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_9)
    freeCompilerArgs.addAll(listOf("-module-name", "TheName"))
    apiVersion.set(KotlinVersion.KOTLIN_1_9)
}
                    """.trimIndent(),
                    listOf(
                        "org.jetbrains.kotlin.gradle.dsl.JvmTarget",
                        "org.jetbrains.kotlin.gradle.dsl.KotlinVersion",
                        "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"
                    )
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartOptionsBeforeDot(): String =
        """
tasks.withType<KotlinCompile>().configureEach {
    <caret>kotlinOptions { options.jvmTarget.set(JvmTarget.JVM_11) }
}
                """.trimIndent()

    @Disabled("KTIJ-38181") // The "After" part should be fixed don't know yet how
    @ParameterizedTest
    @AllGradleVersionsSource
    fun testOptionsBeforeDot(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartOptionsBeforeDot(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { options.jvmTarget.set(JvmTarget.JVM_11) }
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartOptionsBeforeDotInDotQualifiedExpression(): String =
        """
tasks.withType<KotlinCompile>().configureEach {
    <caret>kotlinOptions.options.jvmTarget.set(JvmTarget.JVM_11)
}
                """.trimIndent()

    @Disabled("KTIJ-38181") // The "After" part should be fixed don't know yet how
    @ParameterizedTest
    @AllGradleVersionsSource
    fun testOptionsBeforeDotInDotQualifiedExpression(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartOptionsBeforeDotInDotQualifiedExpression(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.options.jvmTarget.set(JvmTarget.JVM_11)
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartWithSubprojects(): String =
        """
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions {
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
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartWithSubprojects(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
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
                    listOf("org.jetbrains.kotlin.gradle.dsl.JvmTarget", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartGetByName(): String =
        """
tasks.getByName<KotlinCompile>("compileKotlin") {
    <caret>kotlinOptions.allWarningsAsErrors = true
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testGetByName(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(
                    getBuildScriptPartGetByName(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                createBuildScript(
                    """
tasks.getByName<KotlinCompile>("compileKotlin") {
    compilerOptions.allWarningsAsErrors.set(true)
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartGetByNameAndDotReferenced(): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    <caret>kotlinOptions {
        languageVersion = "1.9"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testGetByNameAndDotReferenced(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(getBuildScriptPartGetByNameAndDotReferenced()),
                createBuildScript(
                    """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.KotlinVersion")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

    private fun getBuildScriptPartGetByNameAndLambda(): String =
        """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    <caret>kotlinOptions {
        languageVersion = "1.9"
    }
}
                """.trimIndent()

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testGetByNameAndLambda(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            BASIC_PROJECT_FIXTURE
        ) {
            testIntention(
                createBuildScript(getBuildScriptPartGetByNameAndLambda()),
                createBuildScript(
                    """
tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}
                    """.trimIndent(),
                    listOf("org.jetbrains.kotlin.gradle.dsl.KotlinVersion")
                ),
                "Replace 'kotlinOptions' with 'compilerOptions'"
            )
        }
    }

}
