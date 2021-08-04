// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markers for completion tests that require partial indexing.
 * If you need to reproduce problem with a specific indexing mode,
 * annotate test method with {@link Exact}.
 * @see TestIndexingModeSupporter.IndexingMode
 */
public interface NeedsIndex {

    /**
     * Used to mark those completion tests that are not expected to work in dumb mode even with full indices.
     * Please also provide reason the test fails when you use this annotation.
     *
     * @see com.intellij.java.codeInsight.completion.JavaCompletionTestSuite
     * @see com.jetbrains.php.slowTests.PhpDumbCompletionTestSuite
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface SmartMode {
        String reason();
    }

    /**
     * Used to mark those completion tests that are not expected to work in dumb mode
     * with runtime-only indices (of JDK, php standard libraries, etc.), but work with full indices
     *
     * @see com.intellij.java.codeInsight.completion.JavaCompletionTestSuite
     * @see com.jetbrains.php.slowTests.PhpDumbCompletionTestSuite
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface Full {
        String reason() default "";
    }

    /**
     * Used to mark those completion tests that are not expected to work in dumb mode with empty indices,
     * but works with indices of standard library only (of JDK, php standard libraries, etc.)
     *
     * @see com.intellij.java.codeInsight.completion.JavaCompletionTestSuite
     * @see com.jetbrains.php.slowTests.PhpDumbCompletionTestSuite
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface ForStandardLibrary {
        String reason() default "";
    }

  /**
   * Could be used to quickly debug the test with a corresponding indexing mode
   * @deprecated to avoid accidental commit: should be used for debug purpose only
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @Deprecated
  @interface Exact {
    TestIndexingModeSupporter.IndexingMode value();
  }
}
