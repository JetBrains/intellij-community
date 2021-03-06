/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class CastConflictsWithInstanceofInspectionTest extends LightJavaInspectionTestCase {

  public void testElseElse() { doTest(); }
  public void testSimple() { doTest(); }
  public void testElseElseOrOr() { doTest(); }
  public void testAndAnd() { doTest(); }
  public void testPolyadic() { doTest(); }
  public void testNotOr() { doTest(); }
  public void testOrInstanceofOrInstanceof() { doTest(); }
  public void testIfCheckBefore() { doTest(); }
  public void testIfElseCheckBefore() { doTest(); }
  public void testAssertCheckBefore() { doTest(); }
  public void testAssertionMethodCheckBefore() { doTest(); }
  public void testWhileOrChain() { doTest(); }
  public void testOrCasts() { doTest(); }
  public void testNextOperand() { doTest(); }
  public void testOrNotInWhile() { doTest(); }
  public void testCastConflictsTernaryBooleanFlag() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new CastConflictsWithInstanceofInspection();
  }
}
