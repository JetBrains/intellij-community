// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.indexing;

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.ClassTemplate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs all tests of the class once for each {@link IndexingMode} that the configuration parameter
 * {@link IndexingModeTemplateProvider#MODES_PARAMETER} selects.
 * <p>
 * Without the parameter, the class runs only in {@link IndexingMode#SMART}, as a JUnit 3 test does in a normal run.
 * With the value {@code dumb}, the class runs in the dumb modes, as {@code TestIndexingModeSupporter.addAllTests} runs a JUnit 3 test.
 * With the value {@code all}, the class runs in all modes.
 * The {@code NeedsIndex} annotations on the class remove a mode, and the annotations on a test method skip the test in a mode.
 * <p>
 * A fixture gets the mode of the current run from {@link IndexingModeTemplateProvider#getIndexingMode}.
 * The tag {@link #TAG} lets a {@code @Suite} select the classes.
 */
@TestOnly
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ClassTemplate
@ExtendWith(IndexingModeTemplateProvider.class)
@Tag(IndexingModeTemplate.TAG)
public @interface IndexingModeTemplate {
  /**
   * The tag of each class with this annotation.
   */
  String TAG = "indexing-mode-template";
}
