/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class OctalAndDecimalIntegersMixedInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doStatementTest("int[] is = /*Octal and decimal integers in the same array initializer*/{34, 987, (007), 661}/**/;");
  }

  public void testBinaryLiteral() {
    doStatementTest("int[] elapsed = {1, 13, 0b11001};");
  }

  public void testZeroesAllowed() {
    doStatementTest("long[] ns =  new long[] {033L, 0L, 025L};");
  }

  public void testZeroesAllowed2() {
    doStatementTest("int[] ns =  new int[] {033, 0, 025};");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new OctalAndDecimalIntegersMixedInspection();
  }
}