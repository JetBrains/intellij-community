// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface NeedsIndicesState {

    /**
     * Used to mark those completion tests that are not expected to work in dumb mode even with full indices.
     * Please also provide reason the test fails when you use this annotation.
     *
     * @see com.intellij.java.codeInsight.completion.JavaCompletionTestSuite
     * @see com.jetbrains.php.slowTests.PhpDumbCompletionTestSuite
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
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
    @interface FullIndices {
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
    @interface StandardLibraryIndices {
        String reason() default "";
    }
}
