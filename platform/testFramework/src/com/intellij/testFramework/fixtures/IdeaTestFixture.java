/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.testFramework.fixtures;

/**
 * This is to be provided by IDEA and not by plugin authors.
 */
public interface IdeaTestFixture {

  /**
   * Initializes the fixture.
   * Typically, should be called from your TestCase.setUp() method.
   * @throws Exception any exception thrown during initializing
   */
  void setUp() throws Exception;

  /**
   * Destroys the fixture.
   * Typically, should be called from your TestCase.tearDown() method.
   * @throws Exception any exception thrown during destroying
   */
  void tearDown() throws Exception;
}
