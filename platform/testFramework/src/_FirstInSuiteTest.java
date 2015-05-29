/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import junit.framework.TestCase;

/**
 * This is should be first test in all tests so we can measure how long tests are starting up.
 * @author max
 */
@SuppressWarnings("JUnitTestClassNamingConvention")
public class _FirstInSuiteTest extends TestCase {
  static long suiteStarted;

  public void testNothing() throws Exception {
    suiteStarted = System.nanoTime();
  }

  // performance tests
  public void testNothingPerformance() throws Exception {
    testNothing();
  }
}
