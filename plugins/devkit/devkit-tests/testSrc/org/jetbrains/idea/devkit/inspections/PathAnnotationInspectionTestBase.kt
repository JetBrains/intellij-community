// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.path.PathAnnotationInspection

abstract class PathAnnotationInspectionTestBase : LightJavaCodeInsightFixtureTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_17

  protected abstract fun getFileExtension(): String

  override fun setUp() {
    super.setUp()
    addPlatformClasses()
    IdeaTestUtil.setProjectLanguageLevel(project, LanguageLevel.JDK_17)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    myFixture.enableInspections(PathAnnotationInspection())
  }

  private fun addPlatformClasses() {
    // Add the path annotations
    myFixture.addClass(
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

    myFixture.addClass(
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

    myFixture.addClass(
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

    // Add the NativeContext annotation
    myFixture.addClass(
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

  protected open fun doTest(@Language("JAVA") code: String) {
    val filePath = getTestName(false) + '.' + getFileExtension()
    myFixture.configureByText(filePath, code.trimIndent())
    myFixture.testHighlighting(filePath)
  }

  /**
   * Test that string literals that denote a filename (without slashes) are allowed in places where @Filename is expected.
   */
  fun testStringLiteralFilename() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;
      import java.nio.file.Path;
      import java.nio.file.FileSystem;
      import java.nio.file.FileSystems;

      public class StringLiteralFilename {
          public void testMethod() {
              // String literals that denote a filename (without slashes) should be allowed in Path.of() more parameters
              @MultiRoutingFileSystemPath String basePath = "/base/path";
              Path path1 = Path.of(basePath, "file.txt"); // No warning, "file.txt" is a valid filename
              Path path2 = Path.of(basePath, <warning descr="Elements of 'more' parameter in Path.of() should be annotated with either @MultiRoutingFileSystemPath or @Filename">"invalid/filename"</warning>); // Warning, contains slash

              // String literals that denote a filename should be allowed in FileSystem.getPath() more parameters
              FileSystem fs = FileSystems.getDefault();
              @NativePath String nativePath = "/usr/local/bin";
              fs.getPath(nativePath, "file.txt"); // No warning, "file.txt" is a valid filename
              fs.getPath(nativePath, <warning descr="Elements of 'more' parameter in FileSystem.getPath() should be annotated with either @NativePath or @Filename">"invalid/filename"</warning>); // Warning, contains slash

              // String constants that denote a filename should also be allowed
              final String validFilename = "file.txt";
              final String invalidFilename = "invalid/filename";

              Path path3 = Path.of(basePath, validFilename); // No warning, validFilename is a valid filename
              Path path4 = Path.of(basePath, <warning descr="Elements of 'more' parameter in Path.of() should be annotated with either @MultiRoutingFileSystemPath or @Filename">invalidFilename</warning>); // Warning, contains slash

              fs.getPath(nativePath, validFilename); // No warning, validFilename is a valid filename
              fs.getPath(nativePath, <warning descr="Elements of 'more' parameter in FileSystem.getPath() should be annotated with either @NativePath or @Filename">invalidFilename</warning>); // Warning, contains slash
          }
      }      
      """.trimIndent())
  }
}
