/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.types;

import com.siyeh.ipp.IPPTestCase;

public class InferLambdaParameterTypeIntentionTest extends IPPTestCase {
  
  public void testSimple() {
    doTest("Expand lambda to (String o) -> {...}");
  }

  public void testFile() {
    doTest("Expand lambda to (File o) -> {...}");
  }

  public void testTwoParams() {
    doTest("Expand lambda to (String o1, String o2) -> {...}");
  }

  public void testSimpleWildcard() {
    doTest("Expand lambda to (Integer o) -> {...}");
  }

  public void testAlreadyExist() throws Exception {
    assertIntentionNotAvailable("Expand lambda to (String o) -> {...}");
  }

  public void testCyclicInference() throws Exception {
    assertIntentionNotAvailable("Expand lambda to (Object x) -> {...}");
  }

  public void testNoParams() throws Exception {
    assertIntentionNotAvailable("Expand lambda to () -> {...}");
  }

  public void testCapturedWildcardParams() throws Exception {
    assertIntentionNotAvailable("Expand lambda to (capture of ?) -> {...}");
  }

  @Override
  protected String getIntentionName() {
    return "";
  }

  @Override
  protected String getRelativePath() {
    return "types/inferLambdaParameterType";
  }
}
