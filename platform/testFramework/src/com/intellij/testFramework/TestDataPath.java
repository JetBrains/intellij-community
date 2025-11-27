// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the path to testdata for the current test case class.
 * <p>
 * May use the variable {@code $CONTENT_ROOT} to specify the module content root or
 * {@code $PROJECT_ROOT} to use the project base directory.
 * <p>
 * Affects only navigation to testdata inside the IDE, not actual test execution –
 * unless noted otherwise, for example, in {@link com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt#codeInsightFixture}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TestDataPath {
  String value();
}
