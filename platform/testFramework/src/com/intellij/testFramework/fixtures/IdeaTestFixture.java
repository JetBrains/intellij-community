// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

/**
 * This is to be provided by the test framework and not by plugin authors.
 */
public interface IdeaTestFixture {
  /**
   * Initializes the fixture.
   * Typically, should be called from your {@code TestCase.setUp()} method.
   *
   * @throws Exception any exception thrown during initializing
   */
  void setUp() throws Exception;

  /**
   * Destroys the fixture.
   * Typically, should be called from your {@code TestCase.tearDown()} method.
   *
   * @throws Exception any exception thrown during destroying
   */
  void tearDown() throws Exception;
}
