// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.indexing;

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;
import org.jetbrains.annotations.TestOnly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the indexing modes that {@link IndexingModeArgumentsProvider} gives for the tests of a class.
 * <p>
 * The provider gives these modes for each test, and it does not drop a mode for a {@link com.intellij.testFramework.NeedsIndex}
 * annotation or for a registry value. This is the same as a JUnit 3 suite that runs all tests of a class in one mode,
 * for example a subclass that runs the tests of its parent class again in {@link IndexingMode#DUMB_EMPTY_INDEX}.
 * The annotation is not inherited, so the parent class keeps all modes.
 */
@TestOnly
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IndexingModes {
  IndexingMode[] value();
}
