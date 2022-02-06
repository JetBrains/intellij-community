/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.classlayout;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.classlayout.FinalPrivateMethodInspection;
import com.siyeh.ig.classlayout.FinalStaticMethodInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class FinalStaticMethodFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new FinalStaticMethodInspection();
  }

  public void testSimple() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "public static final/**/ boolean isPositive(int number) {\n" +
                 "    return number > 0;\n" +
                 "}",
                 "public static boolean isPositive(int number) {\n" +
                 "    return number > 0;\n" +
                 "}"
    );
  }

  public void testDoNotFixOnInstanceMethod() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                               "class X {\n" +
                               "  public final boolean isPositive(int number) {\n" +
                               "    return number > 0;\n" +
                               "  }\n" +
                               "}\n");
  }
}
