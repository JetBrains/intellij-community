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
package com.siyeh.ig.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.serialization.TransientFieldInNonSerializableClassInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryConstructorFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryConstructorInspection();
  }

  public void testRemoveDefaultConstructor() {
    doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
           "public class Foo {\n" +
           "    public Foo/**/() {}\n" +
           "}\n",
           "public class Foo {\n" +
           "}\n"
    );
  }

  public void testRemoveConstructorCallingSuper() {
    doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
           "public class Foo {\n" +
           "    public Foo/**/() {\n" +
           "      super();\n" +
           "    }\n" +
           "}\n",
           "public class Foo {\n" +
           "}\n"
    );
  }

  public void testRemoveAnnotatedConstructor() {
    doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
           "public class Foo {\n" +
           "    @Deprecated\n" +
           "    public Foo/**/() {}\n" +
           "}\n",
           "public class Foo {\n" +
           "}\n"
    );
  }

  public void testDoNotFixConstructorWithParameter() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               "public class Foo {\n" +
                               "    public Foo/**/(int i) {}\n" +
                               "}\n"
    );
  }

  public void testDoNotFixPrivateConstructor() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               "public class Foo {\n" +
                               "    private Foo/**/() {}\n" +
                               "}\n"
    );
  }

  public void testDoNotFixConstructorAmongOthers() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               "public class Foo {\n" +
                               "    public Foo/**/() {}\n" +
                               "    public Foo(int i) {}\n" +
                               "}\n"
    );
  }

  public void testDoNotFixNotEmptyConstructor() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               "public class Foo {\n" +
                               "    public Foo/**/() {\n" +
                               "      System.out.println(\"foo\");\n" +
                               "    }\n" +
                               "}\n"
    );
  }

  public void testDoNotFixNotEmptySuperCall() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               "public class Foo extends java.util.Date {\n" +
                               "    public Foo/**/() {\n" +
                               "      super(1, 1, 1);\n" +
                               "    }\n" +
                               "}\n"
    );
  }
}
