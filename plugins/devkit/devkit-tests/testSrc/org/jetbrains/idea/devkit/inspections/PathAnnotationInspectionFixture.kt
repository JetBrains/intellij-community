// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.EDT
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.path.PathAnnotationInspection

/**
 * The fixture of the [PathAnnotationInspection] tests. Create it with [pathAnnotationInspectionFixture].
 *
 * The project has the path annotations of `com.intellij.platform.eel.annotations` and the language level 17.
 */
class PathAnnotationInspectionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val fileExtension: String,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Checks the highlighting of [code] in the file `<test name>.<file extension>`.
   */
  fun doTest(@Language("JAVA") code: String) {
    val filePath = getTestName(false) + '.' + fileExtension
    fixture.configureByText(filePath, code.trimIndent())
    fixture.testHighlighting(filePath)
  }
}

/**
 * Creates a [PathAnnotationInspectionFixture] for one test. Declare it as an instance field.
 *
 * Give it a project of [com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_17].
 * [fileExtension] is the extension of the test file.
 */
@JvmOverloads
fun pathAnnotationInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String = "java",
): TestFixture<PathAnnotationInspectionFixture> = testFixture("pathAnnotationInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  addPlatformClasses(fixture)
  withContext(Dispatchers.EDT) {
    IdeaTestUtil.setProjectLanguageLevel(fixture.project, LanguageLevel.JDK_17)
  }
  IndexingTestUtil.suspendUntilIndexesAreReady(fixture.project)
  fixture.enableInspections(PathAnnotationInspection())
  initialized(PathAnnotationInspectionFixture(fixture, fileExtension)) {}
}

private fun addPlatformClasses(fixture: JavaCodeInsightTestFixture) {
  // Add the path annotations
  fixture.addClass(
    """
      package com.intellij.platform.eel.annotations;

      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      import static java.lang.annotation.ElementType.*;

      /**
       * This annotation should be applied to strings that could be directly used to construct java.nio.file.Path instances.
       * These strings are either local to the IDE process or have prefix pointing to the specific environment.
       */
      @Retention(RetentionPolicy.SOURCE)
      @Target({FIELD, LOCAL_VARIABLE, PARAMETER, METHOD, TYPE_USE})
      public @interface MultiRoutingFileSystemPath {}
    """.trimIndent()
  )

  fixture.addClass(
    """
      package com.intellij.platform.eel.annotations;

      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      import static java.lang.annotation.ElementType.*;

      /**
       * This is the path within the specific environment.
       * For example, for a path in WSL it would be a Unix path within the WSL machine,
       * and for a path in a Docker container it would be a path within this Docker container.
       * <p>
       * It should not be directly used in the java.nio.file.Path ctor and auxiliary methods like java.nio.file.Path.of.
       */
      @Retention(RetentionPolicy.SOURCE)
      @Target({FIELD, LOCAL_VARIABLE, PARAMETER, METHOD, TYPE_USE})
      public @interface NativePath {}
    """.trimIndent()
  )

  fixture.addClass(
    """
      package com.intellij.platform.eel.annotations;

      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      import static java.lang.annotation.ElementType.*;

      /**
       * Denotes that the annotated element represents a simple filename without any path components.
       * This annotation is meant to indicate that the corresponding string values must contain only
       * the bare filename (e.g., "file.txt") without any directory separators, drive letters,
       * or path components (e.g., not "C:\folder\file.txt" or "/home/user/file.txt").
       */
      @Retention(RetentionPolicy.SOURCE)
      @Target({FIELD, LOCAL_VARIABLE, PARAMETER, METHOD, TYPE_USE})
      public @interface Filename {}
    """.trimIndent()
  )

  fixture.addClass(
    """
      package com.intellij.platform.eel.annotations;

      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      import static java.lang.annotation.ElementType.*;

      /**
       * This annotation should be applied to strings that represent local paths.
       * These paths are local to the IDE process.
       */
      @Retention(RetentionPolicy.SOURCE)
      @Target({FIELD, LOCAL_VARIABLE, PARAMETER, METHOD, TYPE_USE})
      public @interface LocalPath {}
    """.trimIndent()
  )

  // Add the NativeContext annotation
  fixture.addClass(
    """
      package com.intellij.platform.eel.annotations;

      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      import static java.lang.annotation.ElementType.*;

      /**
       * @see NativePath
       */
      @Retention(RetentionPolicy.SOURCE)
      @Target({FIELD, LOCAL_VARIABLE, PARAMETER, METHOD, TYPE_USE})
      public @interface NativeContext {}
    """.trimIndent()
  )
}
