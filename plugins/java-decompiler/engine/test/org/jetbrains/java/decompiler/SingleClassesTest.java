/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler;

import org.junit.Test;

public class SingleClassesTest extends SingleClassesTestBase {
  @Test public void testClassFields() { doTest("pkg/TestClassFields"); }
  @Test public void testClassLambda() { doTest("pkg/TestClassLambda"); }
  @Test public void testClassLoop() { doTest("pkg/TestClassLoop"); }
  @Test public void testClassSwitch() { doTest("pkg/TestClassSwitch"); }
  @Test public void testClassTypes() { doTest("pkg/TestClassTypes"); }
  @Test public void testClassVar() { doTest("pkg/TestClassVar"); }
  @Test public void testClassNestedInitializer() { doTest("pkg/TestClassNestedInitializer"); }
  @Test public void testClassCast() { doTest("pkg/TestClassCast"); }
  @Test public void testDeprecations() { doTest("pkg/TestDeprecations"); }
  @Test public void testExtendsList() { doTest("pkg/TestExtendsList"); }
  @Test public void testMethodParameters() { doTest("pkg/TestMethodParameters"); }
  @Test public void testCodeConstructs() { doTest("pkg/TestCodeConstructs"); }
  @Test public void testConstants() { doTest("pkg/TestConstants"); }
  @Test public void testEnum() { doTest("pkg/TestEnum"); }
  @Test public void testDebugSymbols() { doTest("pkg/TestDebugSymbols"); }
  @Test public void testInvalidMethodSignature() { doTest("InvalidMethodSignature"); }
  @Test public void testInnerClassConstructor() { doTest("pkg/TestInnerClassConstructor"); }
  @Test public void testInnerClassConstructor11() { doTest("v11/TestInnerClassConstructor"); }
  @Test public void testTryCatchFinally() { doTest("pkg/TestTryCatchFinally"); }
}
