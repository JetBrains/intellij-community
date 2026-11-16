// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

/**
 * The fixture of the [CompanionObjectInExtensionInspection] tests. Create it with [ktCompanionObjectInExtensionFixture].
 *
 * The project has the stubs of `ExtensionPointName`, `Service`, `Logger` and `JvmStatic`, and the file `plugin.xml` of the test data.
 * The test data files are Kotlin files.
 */
class KtCompanionObjectInExtensionFixture(fixture: LightJavaCodeInsightTestFixture) : LightDevKitInspectionFixture(fixture, "kt") {
  /**
   * Checks the highlighting of `<test name>.kt` and `<test name>_references.<refFileExtension>`, applies the quick fix [fixName],
   * and checks the `_after` files of both. A `_after.k2.kt` file takes the place of the `_after.kt` file when it exists.
   * Call it from any thread. It applies the quick fix on the EDT.
   */
  @JvmOverloads
  fun doTestFixWithReferences(fixName: String, refFileExtension: String = fileExtension) {
    val (fileNameBefore, fileNameAfter) = getBeforeAfterFileNames()
    val (referencesFileNameBefore, referencesFileNameAfter) = getBeforeAfterFileNames(suffix = "references", extension = refFileExtension)
    fixture.testHighlighting(fileNameBefore, referencesFileNameBefore)
    runInEdtAndWait {
      val intention = fixture.findSingleIntention(fixName)
      fixture.launchAction(intention)
      fixture.checkResultByFile(fileNameBefore, fileNameAfter, true)
      fixture.checkResultByFile(referencesFileNameBefore, referencesFileNameAfter, true)
    }
  }

  /**
   * Checks the highlighting of `<test name>.kt` and checks that the quick fix [fixName] reports [expectedConflicts].
   * Call it from any thread. It applies the quick fix on the EDT.
   */
  fun doTestFixWithConflicts(fixName: String, expectedConflicts: List<String>) {
    val fileName = "${fixture.getTestName(false)}.$fileExtension"
    fixture.testHighlighting(fileName)
    runInEdtAndWait {
      val intention = fixture.findSingleIntention(fixName)
      try {
        fixture.launchAction(intention)
        throw AssertionError("Expected ConflictsInTestsException exception te be thrown.")
      }
      catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
        assertThat(e.messages).hasSameSizeAs(expectedConflicts)
        assertThat(e.messages.toSet()).isEqualTo(expectedConflicts.toSet())
      }
    }
  }

  private fun getBeforeAfterFileNames(
    testName: String = fixture.getTestName(false),
    suffix: String? = null,
    extension: String = fileExtension,
  ): Pair<String, String> {
    val resultName = testName + suffix?.let { "_$it" }.orEmpty()
    val beforeName = "${resultName}.$extension"

    val k2FileName = "${resultName}_after.k2.$extension"
    val k2FilePath = fixture.testDataPath + "/" + k2FileName
    if (File(k2FilePath).exists()) {
      return beforeName to k2FileName
    }
    return beforeName to "${resultName}_after.$extension"
  }
}

/**
 * Creates the project of a [CompanionObjectInExtensionInspection] test class: the JDK and the Kotlin standard library.
 * Declare it in a static field or in a companion object.
 */
fun ktCompanionObjectInExtensionProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance())

/**
 * Creates a [KtCompanionObjectInExtensionFixture] for one test. Declare it as an instance field.
 * Give it the project of [ktCompanionObjectInExtensionProjectFixture].
 *
 * [basePath] is the test data path under the community directory. The fixture configures the file `plugin.xml` of it.
 */
fun ktCompanionObjectInExtensionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String,
): TestFixture<KtCompanionObjectInExtensionFixture> = testFixture("ktCompanionObjectInExtensionFixture") {
  val fixture = lightDevKitInspectionFixture(projectFixture, "kt", basePath).init().fixture
  fixture.addClass(
    """
      package com.intellij.openapi.extensions;

      public class ExtensionPointName<T> {
        public ExtensionPointName(String name) { }
      }
    """.trimIndent()
  )

  fixture.addClass(
    """
      import com.intellij.openapi.extensions.ExtensionPointName;

      public interface MyExtension {
        ExtensionPointName<MyExtension> EP_NAME = new ExtensionPointName<>("com.intellij.example.myExtension");
      }
    """.trimIndent()
  )

  fixture.addClass(
    """
      package com.intellij.openapi.components;

      public @interface Service { }
    """.trimIndent()
  )

  fixture.addClass(
    """
      package com.intellij.openapi.diagnostic;

      public class Logger { }
    """.trimIndent()
  )

  fixture.addClass(
    """
    public @interface MyAnnotation { }
    """.trimIndent()
  )

  fixture.addClass(
    """
    package kotlin.jvm;

    public @interface JvmStatic { }
    """.trimIndent()
  )

  fixture.configureByFile("plugin.xml")
  fixture.enableInspections(CompanionObjectInExtensionInspection())
  initialized(KtCompanionObjectInExtensionFixture(fixture)) {}
}
