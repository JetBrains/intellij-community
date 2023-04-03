// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import com.intellij.testFramework.TestDataPath
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.tooling.core.withLinearClosure
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.junit.runner.Description
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Finds the testdata-folder given the [testDescription].
 *
 * [strict]=true will make the function to throw an exception if the found folder doesn't exist
 *
 * General intuition:
 * - @TestDataPath always gives the first part of the path. $PROJECT_ROOT template is supported
 *   Tip: should be present on the common ancestors of all tests with the most
 *   common root for all testdata (like `$PROJECT/allTests`)
 *
 * - @TestMetadata give subsequent parts in order from the farthest supertype to the
 *   concrete class
 *   Tip: it allows to "inherit" test data paths. E.g. some base test class for all
 *   Gradle-related tests can be annotated with 'gradle' (giving `$PROJECT/allTests/gradle`
 *   together with @TestDataPath), and subsequent inheritors can add more specific paths,
 *   e.g. 'multiplatform' for `$PROJECT/allTests/gradle/multiplatform`
 *
 * - Last part is either test name without 'test' or @TestMetadata on test method
 *   Tip: @TestMetadata on method if you want to have test method name to be different from the folder.
 *   This is most often useful when you want to run several tests against same testdata.
 *
 * Such an elaborate algorithm is used mostly to comply with DevKit heuristics, which,
 * specifically, enables 'Navigate To Testdata' actions & gutters.
 */
fun computeTestDataDirectory(testDescription: Description, strict: Boolean = true): File {
    require(testDescription.methodName.startsWith("test")) {
        "Test method names are expected to start with 'test', actual name: ${testDescription.methodName}\n" +
                "Please add 'test' prefix, as it helps DevKit to work (e.g. provide " +
                "Navigate to Test Data gutters)"
    }

    val testDataPathAnnotationValue = getTestDataPathAnnotationValueWithOwner(testDescription)

    val testMetadataOnClassesFromCurrentToSuper = testDescription.testClass.withLinearClosure { it.superclass }
        .mapNotNull { clazz -> clazz.getAnnotation(TestMetadata::class.java)?.value?.let { it to clazz } }

    val testFolderName = getTestFolderName(testDescription)

    val testFolderFullPath = Path(
        testDataPathAnnotationValue.first,
        *testMetadataOnClassesFromCurrentToSuper.asReversed().map { it.first }.toTypedArray(),
        testFolderName
    )

    if (strict && !testFolderFullPath.exists()) {
        val testMetadataValuesRenderedWithOwners =
            testMetadataOnClassesFromCurrentToSuper.joinToString(separator = "\n") { (value, clazz) ->
                "value = $value, found on ${clazz.name}"
            }.nullize()
        error(
            """Can't find test data directory
                | Full path = ${testFolderFullPath.toFile().canonicalPath}
                |
                | @TestDataPath.value = ${testDataPathAnnotationValue.first}
                |     found on: ${testDataPathAnnotationValue.second.name}
                |     
                | @TestMetadata on classes (from current to super):
                | ${testMetadataValuesRenderedWithOwners ?: "not found"} 
                | 
                | testFolderName = ${testFolderName}
                | testMethodName = ${testDescription.methodName}
            """.trimMargin()
        )
    }

    return testFolderFullPath.toFile()
}

private fun getTestDataPathAnnotationValueWithOwner(description: Description): Pair<String, Class<*>> {
    val result = generateSequence(description.testClass) { it.superclass }
        .firstNotNullOfOrNull { clazz -> clazz.getAnnotation(TestDataPath::class.java)?.value?.let { it to clazz } }

    requireNotNull(result) {
        "@TestDataPath annotation is expected to be present either on the class itself, or one the one of the superclasses"
    }

    require(!result.first.contains("\$CONTENT_ROOT")) {
        "\$CONTENT_ROOT pattern in @TestDataPath is not supported yet in MPP Test suites\n" +
                "Please, use \$PROJECT_ROOT instead"
    }

    return result.first.replace("\$PROJECT_ROOT", KotlinRoot.REPO.canonicalPath) to result.second
}

internal fun getTestFolderName(description: Description): String =
    getTestFolderName(description.methodName, description.getAnnotation(TestMetadata::class.java))

internal fun getTestFolderName(testMethodName: String, testMetadataIfAny: TestMetadata?): String {
    val testMetadataOnMethod = testMetadataIfAny?.value
    return testMetadataOnMethod
    // JUnit4 doesn't allow to have display name different from the test name, so have to do some
    // hoops to remove "parameters" in []
        ?: testNamePattern.matchEntire(testMethodName)!!.groups[1]!!.value.decapitalizeAsciiOnly()
}

// expected pattern:
//      testFoo[1.8.20-dev-3308, Gradle 7.5.1, AGP 7.4.0, any other string for describing versions]
//      testFoo // can be used by infra, e.g. see [testSuiteMethodsAndTestdataFoldersConsistency]
private val testNamePattern = """test([^\[]*)(\[.*])?""".toRegex()
