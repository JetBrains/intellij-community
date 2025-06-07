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

    myFixture.addClass(
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
}
