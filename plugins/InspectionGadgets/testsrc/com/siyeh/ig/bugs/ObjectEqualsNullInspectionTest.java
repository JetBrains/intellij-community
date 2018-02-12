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
package com.siyeh.ig.bugs;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ObjectEqualsNullInspectionTest extends IGQuickFixesTestCase {

  public void testCastArgument() {
    doMemberTest(InspectionGadgetsBundle.message("not.equals.to.equality.quickfix"),
                 "boolean m(String s) {" +
                 "  return !s.equals((Integer)/**/(null));" +
                 "}",

                 "boolean m(String s) {" +
                 "  return s != null;" +
                 "}");
  }

  public void testExpressionStatement() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("not.equals.to.equality.quickfix"),
                               "class X {" +
                               "  void m(String s) {" +
                               "    !s.equals(/**/null);" +
                               "  }" +
                               "}");
  }

  @Nullable
  @Override
  protected BaseInspection getInspection() {
    return new ObjectEqualsNullInspection();
  }
}